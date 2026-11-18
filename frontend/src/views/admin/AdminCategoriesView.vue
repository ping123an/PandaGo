<template>
  <div class="admin-categories-page">
    <div class="page-head">
      <div>
        <h1>分类管理</h1>
        <p>仅管理员可操作；同一父分类下名称唯一，禁用分类后不能再新增该分类商品。</p>
      </div>
      <el-button type="primary" @click="openCreate(0)">新增一级分类</el-button>
    </div>

    <div v-loading="loading" class="table-card card">
      <el-table :data="flatCategories" empty-text="暂无分类" stripe row-key="id" default-expand-all>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" min-width="220">
          <template #default="{ row }">
            <span :style="{ paddingLeft: `${row.parentId === 0 ? 0 : 24}px` }">
              {{ row.parentId === 0 ? '' : '└ ' }}{{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="层级" width="100">
          <template #default="{ row }">{{ row.parentId === 0 ? '一级' : '二级' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.parentId === 0" link type="primary" @click="openCreate(row.id)">加子分类</el-button>
            <el-button link @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分类' : '新增分类'" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="分类名称">
          <el-input v-model="form.name" maxlength="50" placeholder="分类名称" />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="图标地址">
          <el-input v-model="form.icon" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminCategories,
  createCategory,
  updateCategory,
  updateCategoryStatus,
} from '@/api/category'
import type { CategoryVO } from '@/types/catalog'

interface FlatCategory {
  id: number
  parentId: number
  name: string
  sort: number
  icon: string | null
  status: number
}

const loading = ref(false)
const categories = ref<CategoryVO[]>([])
const dialogVisible = ref(false)
const editing = ref<CategoryVO | null>(null)
const submitting = ref(false)
const form = reactive({ name: '', sort: 0, icon: '', parentId: 0 })

const flatCategories = computed<FlatCategory[]>(() => {
  const result: FlatCategory[] = []
  for (const parent of categories.value) {
    result.push({ ...parent })
    for (const child of parent.children) {
      result.push({ ...child })
    }
  }
  return result
})

async function loadCategories(): Promise<void> {
  loading.value = true
  try {
    categories.value = (await getAdminCategories()).data.data
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载分类失败')
  } finally {
    loading.value = false
  }
}

function openCreate(parentId: number): void {
  editing.value = null
  form.name = ''
  form.sort = 0
  form.icon = ''
  form.parentId = parentId
  dialogVisible.value = true
}

function openEdit(row: CategoryVO): void {
  editing.value = row
  form.name = row.name
  form.sort = row.sort
  form.icon = row.icon || ''
  form.parentId = row.parentId
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!form.name.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  submitting.value = true
  try {
    const payload = {
      parentId: form.parentId,
      name: form.name.trim(),
      sort: form.sort,
      icon: form.icon.trim() || null,
    }
    if (editing.value) {
      await updateCategory(editing.value.id, payload)
      ElMessage.success('分类已更新')
    } else {
      await createCategory(payload)
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    await loadCategories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: CategoryVO): Promise<void> {
  const next = row.status === 1 ? 0 : 1
  const label = next === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${label}分类「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateCategoryStatus(row.id, next)
    ElMessage.success(`已${label}分类`)
    await loadCategories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

onMounted(loadCategories)
</script>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;

  h1 { margin: 0 0 6px; font-size: 22px; }
  p { margin: 0; color: var(--pd-muted); font-size: 13px; }
}
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; padding: 18px; }
</style>
