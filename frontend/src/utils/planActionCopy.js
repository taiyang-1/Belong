const ACTION_COPY = {
  create: {
    label: 'Your Goal',
    placeholder: 'Describe what you want to plan...',
    hint: '新建：从你的目标生成一个新计划。',
    submitLabel: '生成计划',
    loadingLabel: 'Planning...',
  },
  adjust: {
    label: '想调整什么',
    placeholder: '比如：我已经完成了入门教程，接下来想降低压力继续做...',
    hint: '调整：基于已有计划和当前进展，重新安排下一步。',
    submitLabel: '调整计划',
    loadingLabel: 'Adjusting...',
  },
  review: {
    label: '复盘反馈',
    placeholder: '比如：这周完成了入门教程，但创建 Agent 时卡在提示词设计...',
    hint: '复盘：检查执行情况，总结问题并更新完成度。',
    submitLabel: '复盘计划',
    loadingLabel: 'Reviewing...',
  },
}

export function getPlanActionCopy(action) {
  return ACTION_COPY[action] || ACTION_COPY.create
}
