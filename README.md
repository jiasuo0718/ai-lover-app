# AI恋人 - Flutter 角色对话APP

微信气泡风格的AI角色对话安卓APP，支持多角色、多API、TTS语音朗读。

## 功能
- 微信风格聊天气泡界面
- 自定义角色（名称、性格、开场白、场景设定）
- 支持所有OpenAI兼容API（DeepSeek、豆包方舟、OpenAI等）
- 多API配置切换
- TTS语音朗读（可选）
- 本地存储聊天记录
- 可调节模型参数（temperature、max_tokens）

## 编译方式（GitHub Actions）

1. 将本项目全部文件上传到GitHub仓库
2. 仓库需包含 `.github/workflows/build.yml`
3. push代码后自动触发编译，或在Actions页面手动Run workflow
4. 编译完成后下载 `unsigned-apk` artifact
5. 用MT管理器对APK签名后安装

## 项目结构
```
lib/
├── main.dart                    # 入口
├── models/
│   ├── character.dart           # 角色模型
│   ├── chat_message.dart        # 消息模型
│   └── api_config.dart          # API配置模型
├── services/
│   ├── local_storage_service.dart  # 本地存储
│   ├── openai_api_service.dart     # API调用
│   └── tts_service.dart            # 语音朗读
└── pages/
    ├── home_page.dart           # 主页（角色列表）
    ├── chat_page.dart           # 聊天页
    ├── character_edit_page.dart # 角色编辑
    ├── api_config_page.dart     # API配置
    └── settings_page.dart       # 设置
```

## 依赖
- flutter_tts: 语音朗读
- shared_preferences: 本地存储
- http: 网络请求
- intl: 国际化
