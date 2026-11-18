<template>
  <div class="product-card-wrap">
    <router-link class="product-card card" :to="{ name: 'product-detail', params: { id: product.id } }">
      <div class="cover">
        <img :src="product.mainImage" :alt="product.name" loading="lazy" />
        <span v-if="product.stockStatus === 'OUT_OF_STOCK'" class="sold-out">暂时缺货</span>
      </div>
      <div class="info">
        <p class="category">{{ product.categoryName }}</p>
        <h3 class="name" :title="product.name">{{ product.name }}</h3>
        <p class="subtitle">{{ product.subtitle || '精选好物，放心选购' }}</p>
        <div class="bottom">
          <span class="price"><small>¥</small>{{ product.price.toFixed(2) }}</span>
          <span class="buy-hint">去购买 →</span>
        </div>
        <p class="sales-line">
          <span>已售 {{ product.sales }} 件</span>
          <span class="stock-status" :class="{ 'out': product.stockStatus === 'OUT_OF_STOCK' }">
            {{ product.stockStatus === 'OUT_OF_STOCK' ? '暂时缺货' : '有货' }}
          </span>
        </p>
      </div>
    </router-link>
    <FavoriteButton :product-id="product.id" class="card-favorite" />
  </div>
</template>

<script setup lang="ts">
import type { ProductListItem } from '@/types/catalog'
import FavoriteButton from '@/components/FavoriteButton.vue'

defineProps<{ product: ProductListItem }>()
</script>

<style scoped lang="scss">
.product-card-wrap { position: relative; }
.product-card {
  display: block;
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 28px rgb(0 0 0 / 10%);
  }
}
.card-favorite { position: absolute; top: 10px; right: 10px; }
.cover {
  position: relative;
  height: 200px;
  background: #f3f4f6;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}
.sold-out {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 8px;
  border-radius: 4px;
  background: rgb(31 35 41 / 75%);
  color: #fff;
  font-size: 11px;
}
.info { padding: 14px 16px 16px; }
.category { margin: 0 0 6px; color: var(--pd-primary); font-size: 11px; }
.name { overflow: hidden; margin: 0; font-size: 15px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.subtitle { overflow: hidden; margin: 6px 0 0; color: var(--pd-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.bottom { display: flex; align-items: baseline; justify-content: space-between; margin-top: 14px; }
.price { color: var(--pd-primary); font-size: 20px; font-weight: 700; small { margin-right: 2px; font-size: 13px; } }
.buy-hint {
  padding: 4px 10px;
  border-radius: 4px;
  background: var(--pd-primary);
  color: #fff;
  font-size: 12px;
  white-space: nowrap;
}
.sales-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0 0;
  color: var(--pd-muted);
  font-size: 11px;
}

.stock-status {
  color: #16a34a;
  font-weight: 600;

  &.out { color: #dc2626; }
}
</style>
