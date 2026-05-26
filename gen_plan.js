const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType,
  ShadingType, PageNumber, PageBreak, TableOfContents, LevelFormat
} = require("docx");

// ============ 工具函数 ============
const border = { style: BorderStyle.SINGLE, size: 1, color: "000000" };
const borders = { top: border, bottom: border, left: border, right: border };
const noBorder = { style: BorderStyle.NONE, size: 0 };
const noBorders = { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder };
const cellM = { top: 60, bottom: 60, left: 100, right: 100 };

function C(text, opts = {}) {
  return new TableCell({
    borders: opts.noBorder ? noBorders : borders,
    width: opts.w != null ? { size: opts.w, type: WidthType.DXA } : undefined,
    shading: opts.sh ? { fill: opts.sh, type: ShadingType.CLEAR } : undefined,
    margins: cellM,
    verticalAlign: "center",
    children: [new Paragraph({
      alignment: opts.a || AlignmentType.LEFT,
      spacing: { before: 30, after: 30 },
      children: [new TextRun({ text: text || "", bold: !!opts.b, size: opts.s || 20, font: opts.f || "宋体" })]
    })]
  });
}

function P(text, opts = {}) {
  return new Paragraph({
    spacing: { before: opts.before || 60, after: opts.after || 60 },
    alignment: opts.a || AlignmentType.LEFT,
    border: opts.border,
    children: [new TextRun({ text, bold: !!opts.b, size: opts.s || 22, font: opts.f || "宋体", color: opts.c, italics: !!opts.i })]
  });
}

function H(level, text) {
  return new Paragraph({ heading: level, children: [new TextRun(text)] });
}

function TH(text, w) {
  return C(text, { w, sh: "D9E2F3", b: true, a: AlignmentType.CENTER, s: 18 });
}

function TD(text, w, opts = {}) {
  return C(text, { w, a: opts.a || AlignmentType.CENTER, s: 18, ...opts });
}

function TRow(cells) {
  return new TableRow({ children: cells });
}

// ============ 构建文档内容 ============
function buildDeliverablesTable() {
  const colW = [600, 2200, 3226, 1600, 1400];
  const header = TRow([
    TH("S.No.", colW[0]), TH("Deliverable\n交付件", colW[1]),
    TH("Description\n描述", colW[2]), TH("Format\n格式", colW[3]),
    TH("Due Date\n交付日期", colW[4])
  ]);
  const data = [
    ["01", "项目立项报告", "项目背景、目标、范围、可行性分析、资源需求", "Word", "2025-06-02"],
    ["02", "项目计划简版", "WBS、甘特图、交付件清单、里程碑计划", "Word", "2025-06-02"],
    ["03", "需求规格说明书", "16个功能模块详细需求、用例图、业务流程图", "Word", "2025-06-20"],
    ["04", "系统设计说明书", "系统架构设计、数据库设计、API接口设计、模块详细设计", "Word", "2025-07-04"],
    ["05", "项目最终代码", "前后端源代码、数据库脚本、部署配置", "Git仓库", "2025-08-18"],
    ["06", "项目介绍PPT", "项目概述、核心功能演示、技术亮点、商业价值", "PPT", "2025-08-22"],
    ["07", "项目关闭总结报告", "项目执行总结、问题与经验、后续优化建议", "Word", "2025-08-25"],
    ["08", "个人总结", "团队成员个人工作总结与心得", "Word", "2025-08-25"],
  ];
  return new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: colW,
    rows: [header, ...data.map(r => TRow([
      TD(r[0], colW[0]), C(r[1], { w: colW[1], b: true, s: 18 }),
      C(r[2], { w: colW[2], s: 18 }), TD(r[3], colW[3]), TD(r[4], colW[4])
    ]))]
  });
}

function buildWBSTable() {
  const colW = [500, 2600, 900, 1000, 800, 1000, 2226];
  const header = TRow([
    TH("序号", colW[0]), TH("工作包", colW[1]), TH("工作量\n(人天)", colW[2]),
    TH("前置任务", colW[3]), TH("难度", colW[4]), TH("负责人", colW[5]), TH("说明", colW[6])
  ]);
  const tasks = [
    ["1", "项目启动", "2", "\u2014", "低", "项目经理", "团队组建、环境搭建、项目章程"],
    ["2", "项目规划", "3", "1", "低", "项目经理", "制定项目计划、风险管理计划"],
    ["3", "需求分析", "12", "2", "中", "需求分析师", "16个功能模块需求调研与分析"],
    ["4", "需求评审", "3", "3", "中", "项目经理", "需求评审会议、需求确认签字"],
    ["5", "系统设计", "16", "4", "高", "架构师", "系统架构、数据库、接口、AI模型设计"],
    ["6", "设计评审", "3", "5", "中", "项目经理", "设计评审会议、设计方案确认"],
    ["7", "学生端核心功能（注册画像/测评/方向探索/岗位匹配）实现及测试", "28", "6", "高", "前端+后端", "4个核心模块开发与单元测试"],
    ["8", "AI分析功能（能力差距分析/学习路径规划）实现及测试", "22", "6", "高", "AI工程师+后端", "DeepSeek大模型集成与Prompt工程"],
    ["9", "学习与辅导功能（技能学习/简历优化/模拟面试/进度追踪）实现及测试", "32", "6", "高", "前端+后端+AI", "4个模块开发，含AI对话与评估"],
    ["10", "企业端功能（人才搜索/项目驱动推荐）实现及测试", "26", "6", "高", "后端+AI工程师", "语义匹配与智能推荐算法"],
    ["11", "管理后台（高校/企业/运营）实现及测试", "22", "6", "中", "全栈工程师", "3个管理后台开发"],
    ["12", "AI智能客服系统实现及测试", "16", "6", "中", "AI工程师", "智能问答机器人开发"],
    ["13", "系统集成测试", "12", "7~12", "高", "测试工程师", "集成测试、性能测试、安全测试"],
    ["14", "用户验收测试", "5", "13", "中", "项目经理", "UAT测试、Bug修复"],
    ["15", "部署上线", "5", "14", "中", "运维工程师", "生产环境部署与配置"],
    ["16", "项目文档与PPT", "5", "14", "低", "全员", "整理项目文档与演示材料"],
    ["17", "项目验收", "3", "15,16", "低", "项目经理", "验收会议、交付物确认"],
    ["18", "项目关闭", "2", "17", "低", "项目经理", "总结报告、归档"],
  ];
  const dataRows = tasks.map(r => TRow([
    TD(r[0], colW[0]), C(r[1], { w: colW[1], s: 18 }),
    TD(r[2], colW[2]), TD(r[3], colW[3]),
    TD(r[4], colW[4]), TD(r[5], colW[5]), C(r[6], { w: colW[6], s: 18 })
  ]));
  // 合计行
  const totalRow = TRow([
    C("合计", { w: colW[0] + colW[1], b: true, sh: "E2EFDA", a: AlignmentType.CENTER, s: 18 }),
    C("214", { w: colW[2], b: true, sh: "E2EFDA", a: AlignmentType.CENTER, s: 18 }),
    C("", { w: colW[3], sh: "E2EFDA", s: 18 }),
    C("", { w: colW[4], sh: "E2EFDA", s: 18 }),
    C("", { w: colW[5], sh: "E2EFDA", s: 18 }),
    C("预估工期约 60 个工作日（3个月）", { w: colW[6], sh: "E2EFDA", s: 18 }),
  ]);
  return new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: colW,
    rows: [header, ...dataRows, totalRow]
  });
}

function buildMilestoneTable() {
  const colW = [600, 3200, 2800, 2426];
  const header = TRow([
    TH("序号", colW[0]), TH("里程碑", colW[1]), TH("时间节点", colW[2]), TH("交付物", colW[3])
  ]);
  const data = [
    ["M1", "项目立项完成", "2025-06-02", "项目立项报告、项目计划"],
    ["M2", "需求评审通过", "2025-06-20", "需求规格说明书"],
    ["M3", "设计评审通过", "2025-07-04", "系统设计说明书"],
    ["M4", "核心模块开发完成", "2025-07-25", "学生端+AI分析+企业端功能代码"],
    ["M5", "全部模块开发完成", "2025-08-08", "所有功能模块代码"],
    ["M6", "系统测试通过", "2025-08-15", "测试报告"],
    ["M7", "项目验收上线", "2025-08-25", "项目关闭总结报告"],
  ];
  return new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: colW,
    rows: [header, ...data.map(r => TRow([
      TD(r[0], colW[0], { b: true }), C(r[1], { w: colW[1], s: 18 }),
      TD(r[2], colW[2]), C(r[3], { w: colW[3], s: 18 })
    ]))]
  });
}

function buildGanttTable() {
  const tasks = [
    { id: "1", name: "项目启动", start: 1, dur: 1 },
    { id: "2", name: "项目规划", start: 1, dur: 2 },
    { id: "3", name: "需求分析", start: 2, dur: 3 },
    { id: "4", name: "需求评审", start: 5, dur: 1 },
    { id: "5", name: "系统设计", start: 5, dur: 3 },
    { id: "6", name: "设计评审", start: 8, dur: 1 },
    { id: "7", name: "学生端核心功能开发及测试", start: 6, dur: 4 },
    { id: "8", name: "AI分析功能开发及测试", start: 6, dur: 3 },
    { id: "9", name: "学习与辅导功能开发及测试", start: 7, dur: 4 },
    { id: "10", name: "企业端功能开发及测试", start: 7, dur: 4 },
    { id: "11", name: "管理后台开发及测试", start: 8, dur: 3 },
    { id: "12", name: "AI智能客服开发及测试", start: 9, dur: 2 },
    { id: "13", name: "系统集成测试", start: 10, dur: 2 },
    { id: "14", name: "用户验收测试", start: 12, dur: 1 },
    { id: "15", name: "部署上线", start: 12, dur: 1 },
    { id: "16", name: "项目文档与PPT", start: 11, dur: 2 },
    { id: "17", name: "项目验收", start: 13, dur: 1 },
    { id: "18", name: "项目关闭", start: 13, dur: 1 },
  ];
  const weeks = 14;
  const monthMap = ["6月","6月","6月","6月","7月","7月","7月","7月","8月","8月","8月","8月","8月","8月"];
  const weekLabels = Array.from({ length: weeks }, (_, i) => "W" + (i + 1));

  const idW = 400, taskW = 2400, cellW = 445;
  const colW = [idW, taskW, ...Array(weeks).fill(cellW)];

  const header1 = TRow([
    TH("编号", idW), TH("任务名称", taskW),
    ...weekLabels.map(w => TH(w, cellW))
  ]);
  const header2 = TRow([
    C("", { w: idW, s: 16 }), C("", { w: taskW, s: 16 }),
    ...monthMap.map(m => C(m, { w: cellW, sh: "E9EDF4", a: AlignmentType.CENTER, s: 16 }))
  ]);

  const taskRows = tasks.map(t => {
    const cells = [
      TD(t.id, idW, { s: 16 }), C(t.name, { w: taskW, s: 16 })
    ];
    for (let w = 1; w <= weeks; w++) {
      const active = w >= t.start && w < t.start + t.dur;
      cells.push(C(active ? "\u2588" : "", { w: cellW, a: AlignmentType.CENTER, s: 14, sh: active ? "A8D08D" : undefined, f: "Arial" }));
    }
    return TRow(cells);
  });

  return new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: colW,
    rows: [header1, header2, ...taskRows]
  });
}

// ============ 文档 ============
async function main() {
  const sectionProps = {
    page: {
      size: { width: 11906, height: 16838 },
      margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 }
    }
  };

  const headerFooter = {
    headers: {
      default: new Header({
        children: [P("基于 DeepSeek 大模型的 AI 智能求职辅导平台 \u2014 项目计划简版", {
          s: 16, c: "808080", before: 0, after: 0,
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: "2E75B6", space: 1 } }
        })]
      })
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 0, after: 0 },
          children: [
            new TextRun({ text: "武汉学链科技有限公司  ", size: 16, font: "宋体", color: "808080" }),
            new TextRun({ text: "Page ", size: 16, font: "Arial", color: "808080" }),
            new TextRun({ children: [PageNumber.CURRENT], size: 16, font: "Arial", color: "808080" }),
            new TextRun({ text: " of ", size: 16, font: "Arial", color: "808080" }),
            new TextRun({ children: [PageNumber.TOTAL_PAGES], size: 16, font: "Arial", color: "808080" }),
          ]
        })]
      })
    }
  };

  const doc = new Document({
    styles: {
      default: { document: { run: { font: "宋体", size: 20 } } },
      paragraphStyles: [
        { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 30, bold: true, font: "黑体" },
          paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 0 } },
        { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 26, bold: true, font: "黑体" },
          paragraph: { spacing: { before: 180, after: 100 }, outlineLevel: 1 } },
      ]
    },
    numbering: {
      config: [{
        reference: "nl",
        levels: [{
          level: 0, format: LevelFormat.DECIMAL, text: "%1.",
          alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } }
        }]
      }]
    },
    sections: [
      // ===== 封面页 =====
      {
        properties: sectionProps,
        children: [
          new Paragraph({ spacing: { before: 2400 }, children: [] }),
          P("XX 软件项目计划", { s: 44, b: true, f: "黑体", a: AlignmentType.CENTER, before: 200, after: 100 }),
          P("Software Project Planning", { s: 28, f: "Arial", i: true, a: AlignmentType.CENTER, before: 50, after: 600 }),
          new Table({
            width: { size: 6000, type: WidthType.DXA },
            columnWidths: [2000, 4000],
            rows: [
              TRow([C("项目名称", { w: 2000, sh: "D9E2F3", b: true }), C("基于 DeepSeek 大模型的 AI 智能求职辅导平台", { w: 4000 })]),
              TRow([C("密级", { w: 2000, sh: "D9E2F3", b: true }), C("内部", { w: 4000 })]),
              TRow([C("项目编号", { w: 2000, sh: "D9E2F3", b: true }), C("Project ID_SPP_2025_001", { w: 4000 })]),
              TRow([C("版本", { w: 2000, sh: "D9E2F3", b: true }), C("V1.0", { w: 4000 })]),
              TRow([C("文档编号", { w: 2000, sh: "D9E2F3", b: true }), C("AI-Job-Coach-SPP-V1.0", { w: 4000 })]),
              TRow([C("拟制", { w: 2000, sh: "D9E2F3", b: true }), C("项目组", { w: 4000 })]),
              TRow([C("拟制日期", { w: 2000, sh: "D9E2F3", b: true }), C("2025-05-26", { w: 4000 })]),
            ]
          }),
          new Paragraph({ spacing: { before: 1400 }, children: [] }),
          P("武汉学链科技有限公司  版权所有  不得复制", { s: 18, c: "808080", a: AlignmentType.CENTER }),
          new Paragraph({ children: [new PageBreak()] })
        ]
      },
      // ===== 内容页 =====
      {
        properties: sectionProps,
        ...headerFooter,
        children: [
          // 修订记录
          P("Revision Record \u2014 修订记录", { s: 28, b: true, f: "黑体", a: AlignmentType.CENTER, before: 100, after: 300 }),
          new Table({
            width: { size: 9026, type: WidthType.DXA },
            columnWidths: [1200, 1400, 1400, 1200, 2000, 1826],
            rows: [
              TRow([
                TH("Date\n日期", 1200), TH("Revision Version\n修订版本", 1400),
                TH("CR ID / Defect ID\nCR/Defect号", 1400), TH("Sec No.\n修改章节", 1200),
                TH("Change Description\n修改描述", 2000), TH("Author\n作者", 1826)
              ]),
              TRow([
                TD("2025-05-26", 1200), TD("V1.0", 1400),
                TD("\u2014", 1400), TD("全部", 1200),
                TD("初始版本", 2000), TD("项目组", 1826),
              ])
            ]
          }),
          new Paragraph({ children: [new PageBreak()] }),

          // 目录
          P("Catalog \u2014 目  录", { s: 28, b: true, f: "黑体", a: AlignmentType.CENTER, before: 100, after: 400 }),
          new TableOfContents("Table of Contents", { hyperlink: true, headingStyleRange: "1-2" }),
          new Paragraph({ children: [new PageBreak()] }),

          // ===== 1 项目简介 =====
          H(HeadingLevel.HEADING_1, "1 项目简介"),
          P("1.1 项目背景", { s: 24, b: true, f: "黑体", before: 120, after: 120 }),
          P("2025 年全国高校毕业生人数突破 1200 万，就业市场竞争空前激烈。超过 70% 的大学生在毕业前对自身职业发展方向感到迷茫，尤其在互联网行业，岗位类型繁多、技术栈迭代快、岗位要求差异大，许多学生盲目跟风\u201C转码\u201D却因不了解各岗位的真实技能要求而半途而废，简历海投回复率不足 5%，面试因方向不匹配被淘汰的比例高达 60%。与此同时，互联网企业同样面临\u201C招人难\u201D困境\u2014\u2014HR 高度依赖关键词筛选简历，难以识别具备实际项目能力的候选人。"),
          P("本项目基于 DeepSeek 大模型技术，构建面向大学生与互联网企业双向赋能的 AI 智能求职辅导平台，以\u201C发现方向 \u2192 定位差距 \u2192 提升能力 \u2192 精准匹配\u201D为核心路径，实现从个人职业规划到企业精准招人的全链路智能化闭环。"),
          P("1.2 项目目标", { s: 24, b: true, f: "黑体", before: 120, after: 120 }),
          ...[
            "为大学生用户提供职业方向探索、能力测评、个性化学习路径、简历优化、AI 模拟面试等全流程求职辅导服务",
            "为企业 HR 提供基于语义理解的智能人才搜索和项目驱动人才推荐能力",
            "为高校就业指导中心提供学生求职画像、能力分布分析等数据支撑",
            "构建互联网行业岗位技能标准化词典，实现岗位要求与个人能力的精确量化对比",
            "基于 DeepSeek 大模型实现自然语言理解、智能对话和语义匹配等核心 AI 能力",
          ].map(t => new Paragraph({
            numbering: { reference: "nl", level: 0 },
            spacing: { before: 30, after: 60 },
            children: [new TextRun({ text: t, size: 22, font: "宋体" })]
          })),
          P("1.3 项目范围", { s: 24, b: true, f: "黑体", before: 120, after: 120 }),
          P("本项目覆盖学生端、企业端、高校管理后台、运营管理平台四大用户端，包含用户注册与求职画像、职业能力基线测评、AI 职业方向探索、岗位智能匹配、AI 岗位能力差距分析、个性化学习路径规划、互联网岗位技能学习、AI 简历智能优化、AI 模拟面试与评估、学习进度追踪与激励、企业岗位人才搜索、项目驱动人才智能推荐、高校管理后台、企业管理后台、运营管理平台、AI 智能客服系统共 16 个功能模块。"),
          new Paragraph({ children: [new PageBreak()] }),

          // ===== 2 交付件 =====
          H(HeadingLevel.HEADING_1, "2 交付件"),
          P("项目交付件清单如下，共计 8 项交付物：", { before: 100, after: 120 }),
          buildDeliverablesTable(),
          new Paragraph({ children: [new PageBreak()] }),

          // ===== 3 WBS =====
          H(HeadingLevel.HEADING_1, "3 WBS 工作任务分解"),
          P("本项目的 WBS 将整体工作分解为 18 个工作包，涵盖项目启动到验收交付的全过程。各模块参照功能划分进行组织，估算总工作量为 214 人天。", { before: 100, after: 120 }),
          buildWBSTable(),
          new Paragraph({ children: [new PageBreak()] }),

          // ===== 4 项目甘特图 =====
          H(HeadingLevel.HEADING_1, "4 项目甘特图"),
          P("以下甘特图展示了项目从 2025 年 6 月 1 日至 2025 年 8 月 25 日（约 12 周）的任务进度安排：", { before: 100, after: 120 }),
          buildGanttTable(),
          P("说明：绿色填充表示该任务在本周处于执行状态。多条任务并行开发以缩短工期。", { s: 18, c: "666666", before: 100, after: 100 }),
          P("4.1 关键里程碑", { s: 24, b: true, f: "黑体", before: 200, after: 120 }),
          buildMilestoneTable(),
          new Paragraph({ spacing: { before: 400 }, children: [] }),
          P("武汉学链科技有限公司", { s: 18, c: "808080", a: AlignmentType.CENTER, before: 400, after: 40,
            border: { top: { style: BorderStyle.SINGLE, size: 4, color: "2E75B6", space: 1 } } }),
          P("Copyright \u00A9 2025 XuelianTechnologies Co., Ltd. All Rights Reserved.", { s: 16, c: "808080", a: AlignmentType.CENTER, f: "Arial" }),
        ]
      }
    ]
  });

  const buffer = await Packer.toBuffer(doc);
  const outPath = "D:/企业实训作业/codebuddy/gitcode/项目计划/项目计划简版_AI智能求职辅导平台.docx";
  fs.writeFileSync(outPath, buffer);
  console.log("SUCCESS: " + outPath);
}

main().catch(err => { console.error(err); process.exit(1); });
