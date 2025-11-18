# Enterprise Platform - Spring Boot + React

**Status:** ✅ Production Ready  
**Version:** 2.0 (Generalized Architecture)  
**Last Updated:** December 2024

---

## 📖 Documentation

### 🌟 START HERE
**[docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md)** - Main entry point with quick navigation to everything

### Core Documentation
- **[docs/PROJECT_HISTORY.md](docs/PROJECT_HISTORY.md)** - Complete evolution timeline
- **[docs/TECHNICAL_ARCHITECTURE.md](docs/TECHNICAL_ARCHITECTURE.md)** - Current architecture and patterns
- **[docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)** - How to use modern patterns
- **[docs/FRONTEND_DEVELOPMENT_GUIDE.md](docs/FRONTEND_DEVELOPMENT_GUIDE.md)** - Frontend development guide

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Node.js 18+
- PostgreSQL 14+
- Maven 3.8+

### Development Setup

```bash
# 1. Clone and install dependencies
git clone <repository-url>
cd project
npm install

# 2. Start development environment
./dev.sh

# 3. Access the application
# Frontend: http://localhost:3000
# Backend API: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### Production Setup

```bash
# Start production environment
./prod.sh

# Check service status
./status.sh

# Stop services
./stop.sh
```

### AI Behavior Module Setup

1. **Drop or point to your schemas** – place YAML descriptors under `ai-infrastructure-module/ai-infrastructure-behavior/src/main/resources/behavior/schemas` (or override `ai.behavior.schemas.path`). Run `./ai-infrastructure-module/scripts/schema-doctor.sh` to lint the files before committing.
2. **Enable Liquibase migrations** – the host Spring Boot app must include the bundled change-log so the `behavior_*` tables are created:

   ```yaml
   spring:
     liquibase:
       change-log: classpath:/db/changelog/db.changelog-master.yaml
       enabled: true
   ```

3. **Register projectors and strategies** – wire neutral KPIs by toggling the provided SPIs in `application.yml`:

   ```yaml
   ai:
     behavior:
       processing:
         metrics:
           enabled-projectors:
             - engagementMetricProjector
             - recencyMetricProjector
             - diversityMetricProjector
           highlighted-domains: [ ]
       insights:
         strategies:
           - engagementInsightStrategy
           - segmentInsightStrategy
   ```

4. **Replay and verify signals** – use `./ai-infrastructure-module/scripts/signal-replay.sh <signals.json|jsonl> [base-url]` to push captured payloads through `/api/ai-behavior/signals/batch` and validate the `/users/{id}/metrics` and `/users/{id}/insights` responses.

---

## 🏗️ Project Overview

### Modern Enterprise Platform
- **Backend:** Spring Boot 3.2.0 with Java 21
- **Frontend:** Next.js 15.5.4 with React 19.2.0
- **Database:** PostgreSQL with Liquibase migrations
- **Authentication:** Supabase integration
- **UI Library:** Material-UI v7
- **State Management:** React Query + Context API

### Key Features
✅ **Enterprise Patterns** - Reusable hooks and HOCs  
✅ **Form Validation** - 100% validation coverage  
✅ **Table Management** - Generic hooks, -60% code  
✅ **Error Handling** - 29+ protected components  
✅ **Testing** - 180+ tests, 95%+ coverage  
✅ **Performance** - 50%+ faster, -44% bundle  

---

## 📊 Status

| Achievement | Status | Impact |
|-------------|--------|--------|
| **Redux Elimination** | ✅ 95% | -45KB bundle |
| **Enterprise Patterns** | ✅ Complete | Reusable hooks |
| **Form Modernization** | ✅ Complete | 100% validation |
| **Table Enhancement** | ✅ Complete | -60% code |
| **Error Handling** | ✅ Complete | 29+ components |
| **Testing** | ✅ Complete | 180+ tests |
| **Performance** | ✅ Complete | 50%+ faster |

---

## 👨‍💻 For Developers

### Time to Productivity: ~30 minutes

1. Read **[docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md)** (5 min)
2. Read **[docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)** (20 min)
3. Explore example components (5 min)
4. Start developing!

### Key Patterns
- **Forms:** Use `useAdvancedForm<T>` hook
- **Tables:** Use `useTableLogic<T>` hook
- **Error Handling:** Wrap with `withErrorBoundary`
- **State:** Use Context API + React Query

---

## 📁 Project Structure

```
/workspace/
├── README.md                        ⭐ START HERE
├── docs/
│   ├── PROJECT_OVERVIEW.md         Main documentation entry
│   ├── PROJECT_HISTORY.md          Complete timeline
│   ├── TECHNICAL_ARCHITECTURE.md   Current architecture
│   ├── DEVELOPER_GUIDE.md          Development guide
│   ├── FRONTEND_DEVELOPMENT_GUIDE.md
│   └── PROJECT_GUIDELINES.yaml     Development guidelines
├── backend/                         Spring Boot application
│   ├── src/main/java/
│   ├── src/main/resources/
│   └── README.md
├── frontend/                        Next.js application
│   ├── src/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── views/
│   │   └── contexts/
│   └── README.md
├── dev.sh                          Development startup script
├── prod.sh                         Production startup script
├── status.sh                       Service status checker
├── stop.sh                         Stop all services
└── toDelete/                       Archived documentation
```

---

## 🎯 Key Achievements

### Redux Elimination ✅
- 95% complete
- -45KB bundle
- Zero breaking changes

### Enterprise Modernization ✅
- 6 phases complete
- 3 major hooks
- 100% type safety
- 50%+ performance improvement

### Quality Assurance ✅
- 180+ tests
- 95%+ coverage
- Production ready

---

## 🏆 Success Metrics

| Metric | Value |
|--------|-------|
| **Bundle Size Reduction** | -44% (350KB saved) |
| **Performance Improvement** | 50%+ faster |
| **Code Reduction** | -60% in tables |
| **Type Safety** | 100% |
| **Test Coverage** | 95%+ |
| **Breaking Changes** | 0 |

---

## 🤝 Contributing

1. Read **[docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)**
2. Follow enterprise patterns
3. Write tests
4. Maintain type safety
5. Update documentation

---

## 📞 Support

- **Documentation:** [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md)
- **Development Guide:** [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)
- **Architecture:** [docs/TECHNICAL_ARCHITECTURE.md](docs/TECHNICAL_ARCHITECTURE.md)
- **History:** [docs/PROJECT_HISTORY.md](docs/PROJECT_HISTORY.md)

---

**Status:** ✅ Production Ready  
**Version:** 2.0 (Generalized Architecture)  
**Last Updated:** December 2024

**Start here: [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md)**
