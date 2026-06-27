<template>
  <div>
    <h2 class="page-title">企业首页</h2>
    <div class="page-card" style="text-align:center;padding:60px;">
      <el-icon :size="60" color="#409EFF"><OfficeBuilding /></el-icon>
      <h3 style="margin:16px 0;">欢迎使用 IT 人才招聘服务</h3>
      <p style="color:#909399;">通过技术岗位需求描述，智能匹配最合适的 IT 技术人才</p>
      <el-button type="primary" size="large" @click="$router.push('/enterprise/recommend')" style="margin-top:20px;">开始人才推荐</el-button>
    </div>
  </div>
</template>
