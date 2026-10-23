import * as echarts from 'echarts/core'
import { GraphChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { LabelLayout } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  GraphChart,
  LegendComponent,
  TooltipComponent,
  LabelLayout,
  CanvasRenderer
])

export { echarts }
