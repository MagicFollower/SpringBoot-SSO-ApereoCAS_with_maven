<template>
  <el-sub-menu v-if="hasChildren" :index="String(item.id)">
    <template #title>
      <el-icon><component :is="item.icon || 'Menu'" /></el-icon>
      <span>{{ item.menuName }}</span>
    </template>
    <sidebar-menu-item v-for="child in item.children" :key="child.id" :item="child" />
  </el-sub-menu>

  <el-menu-item v-else :index="item.path || String(item.id)">
    <el-icon><component :is="item.icon || 'Document'" /></el-icon>
    <template #title>{{ item.menuName }}</template>
  </el-menu-item>
</template>

<script setup>
import { computed } from 'vue'

defineOptions({ name: 'SidebarMenuItem' })

const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

const hasChildren = computed(() => Array.isArray(props.item.children) && props.item.children.length > 0)
</script>