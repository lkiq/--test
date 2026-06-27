const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
        HeadingLevel, AlignmentType, BorderStyle, WidthType, ShadingType, LevelFormat } = require('docx');
const fs = require('fs');

const border = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const tableBorders = { top: border, bottom: border, left: border, right: border };

function cell(text, width, fill = "FFFFFF") {
  return new TableCell({
    borders: tableBorders,
    width: { size: width, type: WidthType.DXA },
    shading: { fill, type: ShadingType.CLEAR },
    margins: { top: 80, bottom: 80, left: 120, right: 120 },
    children: [new Paragraph({ children: [new TextRun(text)], spacing: { before: 0, after: 0 } })]
  });
}

function headerCell(text, width) {
  return new TableCell({
    borders: tableBorders,
    width: { size: width, type: WidthType.DXA },
    shading: { fill: "E7F3FF", type: ShadingType.CLEAR },
    margins: { top: 80, bottom: 80, left: 120, right: 120 },
    children: [new Paragraph({ children: [new TextRun({ text, bold: true })], spacing: { before: 0, after: 0 } })]
  });
}

function row(cells) {
  return new TableRow({ children: cells });
}

const doc = new Document({
  styles: {
    default: { document: { run: { font: "Arial", size: 24 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 36, bold: true, font: "Arial", color: "1A1A1A" },
        paragraph: { spacing: { before: 400, after: 240 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 28, bold: true, font: "Arial", color: "2E5AAC" },
        paragraph: { spacing: { before: 320, after: 160 }, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 26, bold: true, font: "Arial", color: "333333" },
        paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 2 } }
    ]
  },
  numbering: {
    config: [
      { reference: "numbers",
        levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
      { reference: "bullets",
        levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] }
    ]
  },
  sections: [{
    properties: {
      page: {
        size: { width: 12240, height: 15840 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 }
      }
    },
    children: [
      new Paragraph({
        heading: HeadingLevel.HEADING_1,
        alignment: AlignmentType.CENTER,
        children: [new TextRun("求职平台登录/注册页面改造计划书")]
      }),
      new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { after: 240 },
        children: [new TextRun({ text: "AI智能求职辅导平台 · 前端界面优化方案", color: "666666", size: 24 })]
      }),

      new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("一、项目背景与目标")] }),
      new Paragraph({
        children: [new TextRun("当前登录/注册页面采用极简卡片式设计，功能可用但视觉表现和交互体验与主流真实求职平台（如智联招聘、BOSS直聘、前程无忧、猎聘）存在较大差距。本次改造旨在通过品牌化视觉、场景化引导、专业化信息架构和真实平台化交互，提升用户首次访问的信任感和转化率，使界面更贴近真实求职平台的使用习惯。")]
      }),

      new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("二、当前页面现状分析")] }),
      new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun("2.1 登录页（LoginView.vue）")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("标题为“AI智能求职辅导平台”，仅突出AI工具属性，缺少求职场景联想。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("仅包含用户名、密码两个输入框，缺少手机号/邮箱登录、验证码、记住密码、第三方登录等真实平台常见能力。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("按钮为单一“登录”，缺少求职者/企业HR切换入口。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("背景为渐变色，卡片居中，缺少品牌图片、插画、Slogan等求职平台氛围元素。")] }),

      new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun("2.2 注册页（RegisterView.vue）")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("标题为“注册新账号”，缺少分角色引导和价值主张说明。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("字段仅用户名、密码、邮箱（选填）、角色单选，缺少手机号、验证码、服务协议、密码强度提示。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("角色选择为简单单选，缺少“求职者/企业HR”的差异化场景说明和后续流程引导。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("视觉风格与登录页一致，但整体表单单薄，缺乏专业感和安全感。")] }),

      new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("三、改造原则")] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, children: [new TextRun({ text: "真实平台化：", bold: true }), new TextRun("参考主流招聘平台（BOSS直聘、智联招聘、猎聘）的登录/注册布局与交互。")] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, children: [new TextRun({ text: "角色场景化：", bold: true }), new TextRun("根据“学生求职者”和“企业HR”两种角色，提供差异化文案、默认角色和后续跳转。")] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, children: [new TextRun({ text: "信任与安全感：", bold: true }), new TextRun("增加服务协议、隐私政策、密码强度、验证码等真实平台必备元素。")] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, children: [new TextRun({ text: "品牌一致性：", bold: true }), new TextRun("统一配色、字体、间距、圆角、阴影，强化“求职平台”而非“工具平台”的品牌认知。")] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, children: [new TextRun({ text: "渐进式体验：", bold: true }), new TextRun("登录与注册可通过标签/链接快速切换，减少页面跳转成本。")] }),

      new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("四、具体改造内容")] }),
      new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun("4.1 整体布局：从单卡片升级为“左图右表单”分屏式")] }),
      new Paragraph({ children: [new TextRun("参考主流招聘平台，登录/注册页采用左右分屏：")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun({ text: "左侧（40%-50%）：", bold: true }), new TextRun("放置品牌插画/Slogan/平台价值点（如“海量优质岗位、AI智能推荐、一键投递”），背景使用品牌色渐变或品牌图片。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun({ text: "右侧（50%-60%）：", bold: true }), new TextRun("放置表单卡片，包含标题、切换标签、表单、按钮、辅助链接。")] }),
      new Paragraph({ children: [new TextRun({ text: "实现方式：", bold: true }), new TextRun("在 LoginView.vue / RegisterView.vue 中使用 flex 或 CSS Grid 实现左右分屏；桌面端左右排列，移动端（<768px）自动折叠为单栏顶部品牌图+下方表单。")] }),

      new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun("4.2 登录页具体改造")] }),
      new Paragraph({ children: [new TextRun({ text: "4.2.1 标题与品牌区", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("将标题“AI智能求职辅导平台”改为“AI求职 · 精准匹配好机会”或“智能求职平台”，突出求职结果。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("增加 Slogan：如“10万+企业入驻，AI助你更快拿Offer”。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("左侧展示 3-4 个平台价值点（图标+文字）：智能推荐、简历优化、名企直招、AI模拟面试。")] }),

      new Paragraph({ children: [new TextRun({ text: "4.2.2 登录方式切换", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("在表单顶部增加“账号密码登录 / 验证码登录”Tab 切换。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("账号密码登录：用户名/手机号/邮箱 + 密码。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("验证码登录：手机号 + 短信验证码（可先用模拟验证码，后续接真实短信服务）。")] }),

      new Paragraph({ children: [new TextRun({ text: "4.2.3 表单增强", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("输入框占位符改为：“请输入手机号/邮箱/用户名”、“请输入密码”。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("增加“记住密码”复选框和“忘记密码？”链接。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("登录按钮文案改为“立即登录”或“登录”，宽度 100%，圆角 8px，主色使用品牌蓝（#2E5AAC）。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("增加“还没有账号？立即注册”链接，并补充“企业用户登录”快捷入口。")] }),

      new Paragraph({ children: [new TextRun({ text: "4.2.4 第三方登录", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("表单底部增加“其他方式登录”区域，放置微信、QQ、钉钉等图标按钮（前端占位，后续可接入OAuth）。")] }),

      new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun("4.3 注册页具体改造")] }),
      new Paragraph({ children: [new TextRun({ text: "4.3.1 角色选择前置", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("进入注册页后先选择“我要找工作”或“我要招人”，不同角色展示不同文案和默认字段。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("求职者：强调“AI推荐岗位、简历优化、模拟面试”。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("企业HR：强调“精准匹配人才、高效管理简历、智能招聘助手”。")] }),

      new Paragraph({ children: [new TextRun({ text: "4.3.2 表单字段扩展", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("手机号：必填，增加中国大陆手机号正则校验。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("短信验证码：必填，提供“获取验证码”按钮（60秒倒计时，可先用前端模拟）。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("密码：增加密码强度条（弱/中/强）和“6-20位字母+数字”提示。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("确认密码：必填，与密码一致性校验。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("服务协议：底部增加“我已阅读并同意《用户服务协议》《隐私政策》”复选框，未勾选不允许提交。")] }),

      new Paragraph({ children: [new TextRun({ text: "4.3.3 注册流程优化", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("注册按钮文案改为“立即注册”或“免费注册”。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("注册成功后弹出“注册成功”提示，并自动跳转登录页或完善资料页。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("已有账号链接改为“已有账号？直接登录”。")] }),

      new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("五、改造对比表")] }),
      new Table({
        width: { size: 9360, type: WidthType.DXA },
        columnWidths: [2340, 3510, 3510],
        rows: [
          row([headerCell("改造项", 2340), headerCell("当前状态", 3510), headerCell("改造后目标", 3510)]),
          row([cell("页面布局", 2340), cell("单卡片居中，渐变背景", 3510), cell("左右分屏：左品牌图+右表单，响应式适配", 3510)]),
          row([cell("品牌表达", 2340), cell("标题为“AI智能求职辅导平台”", 3510), cell("Slogan + 平台价值点 + 品牌插画", 3510)]),
          row([cell("登录方式", 2340), cell("仅用户名+密码", 3510), cell("账号密码 / 验证码登录 Tab 切换", 3510)]),
          row([cell("角色引导", 2340), cell("注册页简单单选角色", 3510), cell("注册前选择“我要找工作/我要招人”，差异化文案", 3510)]),
          row([cell("安全与信任", 2340), cell("无服务协议、无验证码", 3510), cell("服务协议勾选、短信验证码、密码强度条", 3510)]),
          row([cell("辅助功能", 2340), cell("仅注册/登录链接", 3510), cell("记住密码、忘记密码、第三方登录、企业入口", 3510)]),
          row([cell("视觉风格", 2340), cell("紫蓝渐变，通用卡片", 3510), cell("品牌蓝为主，专业、简洁、有平台感", 3510)])
        ]
      }),

      new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("六、技术实现方案")] }),
      new Paragraph({ children: [new TextRun({ text: "6.1 文件与路由", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("LoginView.vue：重构模板结构，新增左侧品牌区和右侧表单区。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("RegisterView.vue：增加角色选择前置步骤，分角色渲染不同注册表单。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("公共样式：建议将登录/注册页公共样式提取到 src/styles/auth.scss 或内联 scoped 样式中复用。")] }),

      new Paragraph({ children: [new TextRun({ text: "6.2 组件库与图标", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("继续使用 Element Plus（已有 el-form、el-input、el-button、el-radio 等）。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("引入 Element Plus 的 el-tabs、el-steps、el-checkbox、el-divider 等组件。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("使用 Element Plus 图标库（如 User, Lock, Message, Phone, ChatDotRound, OfficeBuilding 等）。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("第三方登录图标可先用 SVG 或 Iconfont 占位。")] }),

      new Paragraph({ children: [new TextRun({ text: "6.3 数据与校验", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("扩展 login 和 register 表单数据模型，新增 phone、verifyCode、confirmPassword、agreement 等字段。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("使用 Element Plus 的 rules 增加手机号、验证码、密码强度、确认密码等校验规则。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("验证码按钮增加倒计时状态（loading + disabled）。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("表单提交前校验 agreement 复选框是否勾选。")] }),

      new Paragraph({ children: [new TextRun({ text: "6.4 响应式与适配", bold: true })] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("桌面端（≥992px）：左右分屏，左侧品牌区展示完整插画和Slogan。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("平板端（768px-991px）：左侧品牌区缩小，保留核心价值点。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("移动端（<768px）：左侧品牌区隐藏或折叠为顶部横幅，仅展示表单。")] }),

      new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("七、预期效果")] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, children: [new TextRun("用户打开页面后第一时间感受到“这是求职平台”，而非“这是某个AI工具”。")] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, children: [new TextRun("登录流程提供多种方式，降低用户流失，符合真实招聘App/网站习惯。")] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, children: [new TextRun("注册流程区分角色并强化安全与信任，提升注册完成率和账号合规性。")] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, children: [new TextRun("整体视觉风格统一、专业，具备品牌辨识度，为后续首页、职位列表等页面改造奠定视觉基础。")] }),

      new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun("八、后续可扩展")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("接入真实短信服务商（阿里云/腾讯云）实现短信验证码。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("接入微信/钉钉/QQ OAuth 第三方登录。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("增加“忘记密码”独立页面，支持手机验证码重置密码。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("登录后根据角色自动跳转：求职者→学生首页 / HR→企业首页 / 管理员→管理后台。")] }),
      new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: [new TextRun("增加注册完成后的“完善资料”引导页（学生补简历、企业补公司信息）。")] })
    ]
  }]
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync(process.argv[2] || "求职平台登录注册改造计划书.docx", buffer);
  fs.writeFileSync("C:\\Users\\26025\\runtemp\\docx_gen_status.log", "SUCCESS: file written");
  console.log("Document created successfully");
}).catch(err => {
  fs.writeFileSync("C:\\Users\\26025\\runtemp\\docx_gen_status.log", "ERROR: " + err.message + "\n" + err.stack);
  console.error("Error:", err);
  process.exit(1);
});
