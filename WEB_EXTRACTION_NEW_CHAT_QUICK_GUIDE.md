# Web Extraction - New Chat Session Quick Guide

## 🎯 What You Need

### Minimum (Just Execute):
**1 file**: `/workspace/ai-infrastructure-module/extract_web_module.sh`

### Recommended (Execute + Backup):
**3 files**:
1. `/workspace/ai-infrastructure-module/extract_web_module.sh` ⭐
2. `/workspace/WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md`
3. `/workspace/WEB_EXTRACTION_COMPLETE_PACKAGE.md`

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

Files attached:
- extract_web_module.sh (automated extraction)
- WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md (manual backup)
- WEB_EXTRACTION_COMPLETE_PACKAGE.md (overview)

Please:
1. Review the extraction script
2. Verify it's safe to execute
3. Run the extraction
4. Report results

Location: /workspace/ai-infrastructure-module/
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
chmod +x extract_web_module.sh
./extract_web_module.sh
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
| Script | `/workspace/ai-infrastructure-module/extract_web_module.sh` | Automated extraction |
| Plan | `/workspace/WEB_MODULE_EXTRACTION_IMPLEMENTATION_PLAN.md` | Manual steps |
| Overview | `/workspace/WEB_EXTRACTION_COMPLETE_PACKAGE.md` | All options |
| Checklist | `/workspace/WEB_EXTRACTION_FILES_CHECKLIST.md` | Full file list |

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
