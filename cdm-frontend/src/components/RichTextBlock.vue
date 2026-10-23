<template>
  <div
    :class="['rich-text-block', { 'rich-text-block--compact': compact }]"
    v-html="renderedHtml"
  ></div>
</template>

<script setup>
import { computed } from 'vue'
import { renderRichText } from '../utils/richText'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  emptyText: {
    type: String,
    default: '暂无内容'
  },
  compact: {
    type: Boolean,
    default: false
  }
})

const renderedHtml = computed(() => renderRichText(props.content, { emptyText: props.emptyText }))
</script>

<style>
.rich-text-block {
  color: inherit;
  font-size: inherit;
  line-height: 1.85;
}

.rich-text-block > :first-child {
  margin-top: 0;
}

.rich-text-block > :last-child {
  margin-bottom: 0;
}

.rich-text-block h1,
.rich-text-block h2,
.rich-text-block h3 {
  margin: 0 0 10px;
  color: inherit;
  font-weight: 700;
  line-height: 1.45;
}

.rich-text-block h1 {
  font-size: 1.15em;
}

.rich-text-block h2 {
  font-size: 1.08em;
}

.rich-text-block h3 {
  font-size: 1em;
}

.rich-text-block p,
.rich-text-block ul,
.rich-text-block ol,
.rich-text-block blockquote {
  margin: 0 0 12px;
}

.rich-text-block ul,
.rich-text-block ol {
  padding-left: 1.3em;
}

.rich-text-block li + li {
  margin-top: 6px;
}

.rich-text-block blockquote {
  padding: 10px 14px;
  border-left: 3px solid rgba(64, 158, 255, 0.45);
  border-radius: 12px;
  background: rgba(64, 158, 255, 0.08);
}

.rich-text-block code {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.08);
  font-family: Consolas, 'Courier New', monospace;
  font-size: 0.92em;
}

.rich-text-block strong,
.rich-text-block em,
.rich-text-block del {
  color: inherit;
}

.rich-text-block__empty {
  color: #8a99a5;
}

.rich-text-block--compact p,
.rich-text-block--compact ul,
.rich-text-block--compact ol,
.rich-text-block--compact blockquote {
  margin-bottom: 8px;
}

.rich-text-block--compact li + li {
  margin-top: 4px;
}
</style>
