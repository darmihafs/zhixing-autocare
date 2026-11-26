import { defineStore } from 'pinia'
import { cartApi } from '../api'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    count: 0,
    loading: false
  }),
  getters: {
    selectedItems: (state) => state.items.filter((item) => item.selected && item.available),
    selectedCount() {
      return this.selectedItems.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
    },
    selectedTotal() {
      return this.selectedItems.reduce((sum, item) => sum + Number(item.totalAmount || 0), 0)
    },
    availableItems: (state) => state.items.filter((item) => item.available),
    allSelected() {
      return this.availableItems.length > 0 && this.availableItems.every((item) => item.selected)
    }
  },
  actions: {
    reset() {
      this.items = []
      this.count = 0
      this.loading = false
    },
    async refreshCount() {
      try {
        this.count = Number(await cartApi.count()) || 0
      } catch {
        this.count = 0
      }
    },
    async load() {
      this.loading = true
      try {
        this.items = await cartApi.list()
        this.count = this.items.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
      } finally {
        this.loading = false
      }
    },
    async add(productId, quantity = 1) {
      await cartApi.add({ productId, quantity })
      await this.refreshCount()
    },
    async updateItem(id, patch) {
      await cartApi.update(id, patch)
      await this.load()
    },
    async remove(id) {
      await cartApi.remove(id)
      this.items = this.items.filter((item) => item.id !== id)
      await this.refreshCount()
    },
    async selectAll(selected) {
      await cartApi.selectAll(Boolean(selected))
      await this.load()
    },
    async clearSelected() {
      await cartApi.removeSelected()
      this.items = this.items.filter((item) => !item.selected)
      await this.refreshCount()
    }
  }
})
