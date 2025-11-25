# Web Extraction - New Chat Session Quick Guide

## 🎯 What You Need

### Minimum (Just Execute):
**1 file**: `/workspace/ai-infrastructure-module/docs/ARCH_REFACTORING/WEB_EXTRACTION/extract_web_module.sh`

### Recommended (Execute + Backup):
**Copy entire WEB_EXTRACTION directory**:
`/workspace/ai-infrastructure-module/docs/ARCH_REFACTORING/WEB_EXTRACTION/`

Contains:
1. `extract_web_module.sh` ⭐
2. `WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md`
3. `WEB_EXTRACTION_COMPLETE_PACKAGE.md`
4. All other supporting docs

---

## 🚀 Prompt for New Chat

Copy and paste this to your new chat session:

```
I need to extract REST controllers from ai-infrastructure-core 
to a new ai-infrastructure-web module.

Summary:
- 6 controllers (1,171 lines, 59 endpoints)
- Automated script available
- Decision: Extract ALL controllers

Directory attached:
- WEB_EXTRACTION/ (entire directory from ARCH_REFACTORING/)
  - extract_web_module.sh (automated extraction)
  - WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md (manual backup)
  - WEB_EXTRACTION_COMPLETE_PACKAGE.md (overview)
  - All supporting docs

Please:
1. Review the extraction script
2. Verify it's safe to execute
3. Run the extraction
4. Report results

Script location: /workspace/ai-infrastructure-module/docs/ARCH_REFACTORING/WEB_EXTRACTION/
Run from: /workspace/ai-infrastructure-module/
```

Then attach the 3 files listed above.

---

## 📋 What the Script Does

1. Creates `ai-infrastructure-web` directory structure
2. Creates `pom.xml` with correct dependencies
3. Copies all 6 controllers from core to web
4. Updates package declarations (`controller` → `web.controller`)
5. Creates configuration classes (AutoConfiguration, Properties)
6. Creates README.md
7. Builds the new module
8. Reports success/failure

**Time**: ~5 minutes  
**Automation**: 100% automated  
**Risk**: Low (safe to run)

---

## 🎯 Quick Command

In new chat, after uploading files:

```bash
cd /workspace/ai-infrastructure-module
chmod +x docs/ARCH_REFACTORING/WEB_EXTRACTION/extract_web_module.sh
./docs/ARCH_REFACTORING/WEB_EXTRACTION/extract_web_module.sh
```

---

## ✅ Expected Result

After successful execution:

```
ai-infrastructure-web/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/ai/infrastructure/web/
│   │   │   ├── controller/ (6 controllers)
│   │   │   └── config/ (2 config classes)
│   │   └── resources/META-INF/spring/
│   │       └── AutoConfiguration.imports
│   └── test/
│       └── java/com/ai/infrastructure/web/

BUILD SUCCESS
```

---

## 📁 Files Location Reference

| File | Location | Purpose |
|------|----------|---------|
| Script | `docs/ARCH_REFACTORING/WEB_EXTRACTION/extract_web_module.sh` | Automated extraction |
| Plan | `docs/ARCH_REFACTORING/WEB_EXTRACTION/WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md` | Manual steps |
| Overview | `docs/ARCH_REFACTORING/WEB_EXTRACTION/WEB_EXTRACTION_COMPLETE_PACKAGE.md` | All options |
| Checklist | `docs/ARCH_REFACTORING/WEB_EXTRACTION/WEB_EXTRACTION_FILES_CHECKLIST.md` | Full file list |

**Base directory**: `/workspace/ai-infrastructure-module/`

---

## 🆘 If Script Fails

Use manual implementation plan:
- Follow Phase 1-7 in `WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md`
- Estimated time: 2-3 days manual

---

## 📞 Quick Reference

**Verify controllers exist**:
```bash
ls ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/
```

**Build after extraction**:
```bash
mvn clean install
```

**Test new module**:
```bash
cd ai-infrastructure-web && mvn test
```

---

**Ready to go!** Upload the files and use the prompt above. 🚀
