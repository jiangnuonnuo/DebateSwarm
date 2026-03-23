export const adminMenuGroups = [
    {
        name: 'workflow',
        label: '工作流管理',
        items: [
            { key: 'flow', label: '流程配置', path: '/admin/flow' },
            { key: 'template', label: '模板库', path: '/admin/template' }
        ]
    },
    {
        name: 'model',
        label: '智能体管理',
        items: [
            { key: 'agent', label: '智能体列表', path: '/admin/agent' },
            { key: 'client', label: '执行节点', path: '/admin/client' },
            { key: 'config', label: '节点配置', path: '/admin/config' }
        ]
    },
    {
        name: 'base',
        label: '基础设施',
        items: [
            { key: 'api', label: '接口定义', path: '/admin/api' },
            { key: 'model', label: '模型定义', path: '/admin/model' },
            { key: 'mcp', label: '工具定义', path: '/admin/mcp' },
            { key: 'prompt', label: '提示词库', path: '/admin/prompt' },
            { key: 'advisor', label: '顾问定义', path: '/admin/advisor' }
        ]
    },
    {
        name: 'user',
        label: '用户管理',
        items: [
            { key: 'user', label: '用户列表', path: '/admin/user' }
        ]
    },
    {
        name: 'other',
        label: '系统运营',
        items: [
            { key: 'task', label: '定时任务', path: '/admin/task' },
            { key: 'plaza', label: '广场内容', path: '/admin/plaza' },
            { key: 'session', label: '会话审计', path: '/admin/session' }
        ]
    }
];
