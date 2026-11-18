import 'vue-router'

/** 路由元信息扩展：布局分组、标题、阶段标记 */
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    description?: string
    group?: 'user' | 'merchant' | 'admin'
    phase?: string
    /** 阶段 3 起启用：需要登录才能访问 */
    requiresAuth?: boolean
    /** 阶段 3 起启用：需要指定角色才能访问 */
    requiresRole?: 'merchant' | 'admin'
  }
}

export {}
