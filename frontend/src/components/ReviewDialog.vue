<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { reviewApi } from '../api'
import { money, resolveImage } from '../utils/format'

const MAX_IMAGES = 5
const MAX_SIZE = 5 * 1024 * 1024
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: null },
  item: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'submitted'])

const score = ref(5)
const content = ref('')
const images = ref([])
const submitting = ref(false)
const uploading = ref(false)
const fileInput = ref(null)

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      score.value = 5
      content.value = ''
      images.value = []
    }
  }
)

function fallback(event) {
  event.target.src = '/images/product/placeholder.jpg'
}

function pickImages() {
  if (uploading.value || images.value.length >= MAX_IMAGES) return
  fileInput.value?.click()
}

async function onFiles(event) {
  const selected = Array.from(event.target.files || [])
  event.target.value = ''
  if (!selected.length) return
  const remaining = MAX_IMAGES - images.value.length
  if (remaining <= 0) {
    ElMessage.warning('最多添加 5 张图片')
    return
  }
  const files = selected.slice(0, remaining)
  if (selected.length > remaining) {
    ElMessage.warning(`最多添加 5 张图片，本次仅上传前 ${remaining} 张`)
  }
  uploading.value = true
  try {
    for (const file of files) {
      if (!ALLOWED_TYPES.includes(file.type)) {
        ElMessage.warning(`${file.name} 不是支持的图片格式`)
        continue
      }
      if (file.size > MAX_SIZE) {
        ElMessage.warning(`${file.name} 超过 5MB`)
        continue
      }
      try {
        const uploaded = await reviewApi.upload(file)
        images.value = [...images.value, uploaded.url]
      } catch {
        /* 拦截器已提示具体原因 */
      }
    }
  } finally {
    uploading.value = false
  }
}

function removeImage(index) {
  images.value = images.value.filter((_, i) => i !== index)
}

async function submit() {
  if (!props.order || !props.item) return
  if (!score.value) {
    ElMessage.warning('请选择评分')
    return
  }
  if (uploading.value) {
    ElMessage.warning('图片还在上传，请稍候')
    return
  }
  submitting.value = true
  try {
    const result = await reviewApi.create({
      orderNo: props.order.orderNo,
      itemId: props.item.itemId,
      score: score.value,
      content: content.value.trim() || undefined,
      images: images.value.length ? images.value : undefined
    })
    ElMessage.success('评价发布成功')
    emit('submitted', result)
    visible.value = false
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="发布评价" width="520px" :close-on-click-modal="false">
    <div v-if="item" class="item-brief">
      <img :src="resolveImage(item.itemImg)" :alt="item.itemName" @error="fallback" />
      <div>
        <strong>{{ item.itemName }}</strong>
        <span>订单 {{ order?.orderNo }}</span>
        <b>¥{{ money(item.price) }} × {{ item.quantity }}</b>
      </div>
    </div>

    <div class="form-row">
      <span class="label">本次评分</span>
      <el-rate v-model="score" size="large" />
      <span class="score-text">{{ score }} 星</span>
    </div>

    <div class="form-row column">
      <span class="label">使用感受</span>
      <el-input
        v-model="content"
        type="textarea"
        :rows="4"
        maxlength="1000"
        show-word-limit
        placeholder="说说商品品质、发货或使用体验，最多 1000 字"
      />
    </div>

    <div class="form-row column">
      <span class="label">晒图（选填）</span>
      <div class="image-list">
        <div v-for="(img, index) in images" :key="img" class="image-chip">
          <img :src="img" :alt="`晒图 ${index + 1}`" @error="fallback" />
          <button type="button" title="删除图片" @click="removeImage(index)">×</button>
        </div>
        <button
          v-if="images.length < MAX_IMAGES"
          type="button"
          class="upload-tile"
          :disabled="uploading"
          @click="pickImages"
        >
          <el-icon><Plus /></el-icon>
          <span>{{ uploading ? '上传中…' : '添加图片' }}</span>
        </button>
      </div>
      <input
        ref="fileInput"
        class="file-input"
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        multiple
        @change="onFiles"
      />
      <p class="hint">支持 JPG、PNG、WebP、GIF，单张不超过 5MB，最多 5 张。</p>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交评价</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.item-brief {
  display: flex;
  gap: 13px;
  padding: 13px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  border: 1px solid var(--line);
}

.item-brief img {
  width: 68px;
  height: 68px;
  border-radius: 6px;
  object-fit: cover;
}

.item-brief strong {
  display: block;
  font-size: 13px;
  line-height: 1.45;
}

.item-brief span {
  display: block;
  margin-top: 3px;
  font-size: 10px;
  color: var(--muted);
}

.item-brief b {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--price);
}

.form-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 20px;
}

.form-row.column {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.label {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-soft);
}

.score-text {
  font-size: 11px;
  color: var(--muted);
}

.file-input {
  display: none;
}

.upload-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 64px;
  height: 64px;
  border: 1px dashed var(--line-strong, #d0d5dd);
  border-radius: 6px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 10px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.upload-tile:hover:not(:disabled) {
  border-color: var(--brand);
  color: var(--brand-deep);
}

.upload-tile:disabled {
  cursor: progress;
  opacity: 0.7;
}

.hint {
  font-size: 10px;
  line-height: 1.6;
  color: var(--muted);
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.image-chip {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--line);
}

.image-chip img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-chip button {
  position: absolute;
  right: 2px;
  top: 2px;
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 50%;
  background: rgba(16, 24, 40, 0.72);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}
</style>
