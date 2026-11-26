<script setup>
import { computed } from 'vue'
import { priceParts } from '../utils/format'

const props = defineProps({
  value: { type: [Number, String], default: 0 }
})

const parts = computed(() => priceParts(props.value))
</script>

<template>
  <span class="price-text"><em>¥</em><b>{{ parts.int }}</b><i>.{{ parts.decimal }}</i></span>
</template>
