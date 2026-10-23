let pdfDependencyPromise

function loadPdfDependencies() {
  if (!pdfDependencyPromise) {
    pdfDependencyPromise = Promise.all([
      import('html2canvas'),
      import('jspdf')
    ]).then(([html2canvasModule, jspdfModule]) => ({
      html2canvas: html2canvasModule.default,
      jsPDF: jspdfModule.jsPDF
    }))
  }
  return pdfDependencyPromise
}

function waitForImages(element) {
  const images = Array.from(element.querySelectorAll('img'))
  if (images.length === 0) {
    return Promise.resolve()
  }
  return Promise.allSettled(images.map((img) => {
    if (img.complete) {
      return Promise.resolve()
    }
    return new Promise((resolve) => {
      const done = () => resolve()
      img.addEventListener('load', done, { once: true })
      img.addEventListener('error', done, { once: true })
    })
  })).then(() => undefined)
}

function sanitizeFileName(value) {
  return (value || '导出文档')
    .replace(/[\\/:*?"<>|]+/g, '-')
    .replace(/\s+/g, '_')
}

function removeIgnoredNodes(root) {
  root.querySelectorAll('[data-pdf-ignore="true"]').forEach((node) => node.remove())
}

function applyPrintStabilityHints(root) {
  root.querySelectorAll('.pdf-cover-card, .plan-card, .report-header').forEach((node) => {
    node.style.breakInside = 'avoid'
    node.style.pageBreakInside = 'avoid'
  })
}

function createRenderRoot(element) {
  const sourceWidth = Math.ceil(Math.max(
    element.scrollWidth,
    element.clientWidth,
    element.getBoundingClientRect().width,
    760
  ))

  const host = document.createElement('div')
  host.style.position = 'fixed'
  host.style.left = '-100000px'
  host.style.top = '0'
  host.style.width = `${sourceWidth}px`
  host.style.padding = '0'
  host.style.margin = '0'
  host.style.pointerEvents = 'none'
  host.style.zIndex = '-1'
  host.style.background = '#ffffff'

  const clone = element.cloneNode(true)
  clone.style.width = '100%'
  clone.style.margin = '0'
  clone.style.background = '#ffffff'

  removeIgnoredNodes(clone)
  applyPrintStabilityHints(clone)

  host.appendChild(clone)
  document.body.appendChild(host)

  return {
    host,
    clone,
    width: sourceWidth
  }
}

async function renderCanvas(element, scale, html2canvas) {
  await waitForImages(element)
  return html2canvas(element, {
    backgroundColor: '#ffffff',
    scale,
    useCORS: true,
    logging: false
  })
}

function addCanvasAsPagedImage(pdf, canvas, margin, addPageFirst = false) {
  const pageWidth = pdf.internal.pageSize.getWidth()
  const pageHeight = pdf.internal.pageSize.getHeight()
  const innerWidth = pageWidth - margin * 2
  const innerHeight = pageHeight - margin * 2
  const contentHeight = (canvas.height * innerWidth) / canvas.width
  const imageData = canvas.toDataURL('image/png')

  let renderedHeight = 0
  let firstChunk = true
  while (renderedHeight < contentHeight) {
    if ((addPageFirst && firstChunk) || (!firstChunk)) {
      pdf.addPage()
    }
    pdf.addImage(
      imageData,
      'PNG',
      margin,
      margin - renderedHeight,
      innerWidth,
      contentHeight,
      undefined,
      'FAST'
    )
    renderedHeight += innerHeight
    firstChunk = false
  }
}

function getPdfBlocks(root) {
  const blocks = Array.from(root.querySelectorAll('[data-pdf-block="true"]'))
  if (blocks.length > 0) {
    return blocks
  }
  return Array.from(root.children)
}

function createPageContainer(width, gap) {
  const page = document.createElement('div')
  page.style.width = `${width}px`
  page.style.boxSizing = 'border-box'
  page.style.background = '#ffffff'
  page.style.display = 'flex'
  page.style.flexDirection = 'column'
  page.style.gap = `${gap}px`
  return page
}

function cloneBlockForPage(block) {
  const clone = block.cloneNode(true)
  clone.style.margin = '0'
  return clone
}

function buildPages(blocks, width, maxPageHeight, gap) {
  const pages = []
  let currentPage = createPageContainer(width, gap)
  let currentHeight = 0

  blocks.forEach((block) => {
    const blockHeight = Math.ceil(block.getBoundingClientRect().height)
    const gapHeight = currentPage.childElementCount > 0 ? gap : 0

    if (blockHeight > maxPageHeight) {
      if (currentPage.childElementCount > 0) {
        pages.push({ element: currentPage, oversized: false })
        currentPage = createPageContainer(width, gap)
        currentHeight = 0
      }

      const oversizedPage = createPageContainer(width, gap)
      oversizedPage.appendChild(cloneBlockForPage(block))
      pages.push({ element: oversizedPage, oversized: true })
      return
    }

    if (currentHeight + gapHeight + blockHeight > maxPageHeight && currentPage.childElementCount > 0) {
      pages.push({ element: currentPage, oversized: false })
      currentPage = createPageContainer(width, gap)
      currentHeight = 0
    }

    currentPage.appendChild(cloneBlockForPage(block))
    currentHeight += (currentPage.childElementCount > 1 ? gap : 0) + blockHeight
  })

  if (currentPage.childElementCount > 0) {
    pages.push({ element: currentPage, oversized: false })
  }

  return pages
}

export async function exportElementToPdf(element, options = {}) {
  if (!element) {
    throw new Error('导出区域不存在')
  }

  const { html2canvas, jsPDF } = await loadPdfDependencies()

  const {
    filename = '导出文档.pdf',
    margin = 10,
    scale = 2,
    blockGap = 16
  } = options

  const pdf = new jsPDF({
    orientation: 'portrait',
    unit: 'mm',
    format: 'a4',
    compress: true
  })

  const renderRoot = createRenderRoot(element)

  try {
    const pageWidth = pdf.internal.pageSize.getWidth()
    const pageHeight = pdf.internal.pageSize.getHeight()
    const innerWidth = pageWidth - margin * 2
    const innerHeight = pageHeight - margin * 2
    const maxPageHeight = Math.floor(renderRoot.width * innerHeight / innerWidth)
    const blocks = getPdfBlocks(renderRoot.clone)
    const pages = buildPages(blocks, renderRoot.width, maxPageHeight, blockGap)

    if (pages.length === 0) {
      const canvas = await renderCanvas(renderRoot.clone, scale, html2canvas)
      addCanvasAsPagedImage(pdf, canvas, margin, false)
    } else {
      for (let index = 0; index < pages.length; index += 1) {
        const page = pages[index]
        renderRoot.host.appendChild(page.element)
        const canvas = await renderCanvas(page.element, scale, html2canvas)
        page.element.remove()

        if (page.oversized) {
          addCanvasAsPagedImage(pdf, canvas, margin, index > 0)
          continue
        }

        if (index > 0) {
          pdf.addPage()
        }
        const imageHeight = (canvas.height * innerWidth) / canvas.width
        const imageData = canvas.toDataURL('image/png')
        pdf.addImage(
          imageData,
          'PNG',
          margin,
          margin,
          innerWidth,
          imageHeight,
          undefined,
          'FAST'
        )
      }
    }
  } finally {
    renderRoot.host.remove()
  }

  pdf.save(sanitizeFileName(filename.endsWith('.pdf') ? filename : `${filename}.pdf`))
}
