<template>
  <el-dialog
    :model-value="visible"
    title="评价商品"
    width="480px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <p class="review-hint">订单 {{ orderNo }} · 商品：{{ item.productName }}</p>
    <el-form label-width="72px">
      <el-form-item label="评分">
        <el-rate v-model="rating" :colors="['#99A9BF', '#F7BA2A', '#FF9900']" />
      </el-form-item>
      <el-form-item label="评价内容">
        <el-input
          v-model="content"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="分享你的使用体验（5~500 字）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交评价</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createReview } from '@/api/review'
import type { OrderItem } from '@/types/order'

const props = defineProps<{
  visible: boolean
  orderNo: string
  item: OrderItem
}>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submitted'): void
}>()

const rating = ref(5)
const content = ref('')
const submitting = ref(false)

watch(
  () => props.visible,
  (open) => {
    if (open) {
      rating.value = 5
      content.value = ''
    }
  },
)

async function submit(): Promise<void> {
  if (rating.value < 1 || rating.value > 5) {
    ElMessage.warning('请选择 1~5 分')
    return
  }
  if (content.value.trim().length < 5) {
    ElMessage.warning('评价内容至少 5 个字符')
    return
  }
  submitting.value = true
  try {
    await createReview({
      orderItemId: props.item.id,
      rating: rating.value,
      content: content.value.trim(),
    })
    ElMessage.success('评价提交成功')
    emit('update:visible', false)
    emit('submitted')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '评价提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.review-hint { margin: 0 0 16px; color: var(--pd-muted); font-size: 13px; }
</style>
