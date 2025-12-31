# Landing Page Diff Prompt - AI Fabric Framework

## 🎯 Context
A landing page was generated at https://aifabric.lovable.app/ using Lovable. This document provides specific changes needed to match the requirements.

---

## 🔧 Required Changes

### **1. HERO SECTION - Above the Fold**

#### **Current Issues:**
- Missing animated/typing code example
- CTA button needs more prominence
- Missing GitHub star count badge
- Tagline may not emphasize "5 months → 5 minutes" contrast

#### **Required Changes:**

```diff
+ Add animated code snippet with typing effect:
  
  <AnimatedCode>
    @Entity
    @AICapable(entityType = "product")
    public class Product {
        // That's it. AI-powered. ✨
    }
  </AnimatedCode>

+ Add GitHub stats badges prominently:
  ⭐ Star us on GitHub | 🔄 Active Development | 📅 2026 Release

+ Make headline more dramatic:
  - Current: [whatever it shows]
  - Change to: "Stop Building AI Infrastructure. Start Building AI Features."
  - Subheadline: "Add AI to Spring Boot in 5 minutes instead of 5 months. Launching Q2 2026."

+ CTA button should be LARGE and use gradient:
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%)
  padding: 1rem 3rem
  font-size: 1.25rem
```

---

### **2. PROBLEM COMPARISON SECTION**

#### **Required: Two-Column "Before/After" Comparison**

Add this exact section if missing:

```jsx
<section className="py-16 bg-gray-50">
  <h2 className="text-4xl font-bold text-center mb-12">
    The Problem Every Dev Team Faces
  </h2>
  
  <div className="max-w-6xl mx-auto grid md:grid-cols-2 gap-8 px-4">
    {/* OLD WAY */}
    <div className="bg-white p-8 rounded-lg border-2 border-red-200">
      <h3 className="text-2xl font-bold mb-4 text-red-600">
        ❌ The Old Way
      </h3>
      <div className="space-y-2 text-gray-700">
        <p>Week 1-2: OpenAI Integration</p>
        <p>Week 3-4: Vector Database</p>
        <p>Week 5-6: Embedding Pipeline</p>
        <p>Week 7-8: Search Logic</p>
        <p>Week 9-10: Async Processing</p>
        <p>Week 11-12: Caching Layer</p>
        <p>Week 13-14: Privacy Controls</p>
        <p>Week 15-16: Data Migration</p>
        <div className="pt-4 border-t-2 border-red-200 mt-4">
          <p className="text-2xl font-bold text-red-600">= 4 MONTHS</p>
        </div>
      </div>
    </div>

    {/* NEW WAY */}
    <div className="bg-gradient-to-br from-blue-50 to-purple-50 p-8 rounded-lg border-2 border-green-400">
      <h3 className="text-2xl font-bold mb-4 text-green-600">
        ✅ The AI Fabric Way
      </h3>
      <div className="space-y-2 text-gray-700">
        <p>5 minutes:</p>
        <p>• Add dependency</p>
        <p>• Add annotation</p>
        <p>• Search works ✨</p>
        <div className="pt-4 border-t-2 border-green-200 mt-8">
          <p className="text-2xl font-bold text-green-600">= 5 MINUTES</p>
        </div>
      </div>
    </div>
  </div>
</section>
```

---

### **3. COST BREAKDOWN SECTION**

#### **Required: Three Cards Layout**

Add if missing:

```jsx
<section className="py-16">
  <h2 className="text-4xl font-bold text-center mb-12">
    What It Really Costs
  </h2>
  
  <div className="max-w-6xl mx-auto grid md:grid-cols-3 gap-8 px-4">
    {/* TIME CARD */}
    <div className="bg-white p-8 rounded-lg shadow-lg text-center">
      <div className="text-6xl mb-4">⏰</div>
      <h3 className="text-2xl font-bold mb-4">TIME</h3>
      <p className="text-4xl font-bold text-blue-600 mb-2">4-6 months</p>
      <p className="text-gray-600">infrastructure development</p>
    </div>

    {/* MONEY CARD */}
    <div className="bg-white p-8 rounded-lg shadow-lg text-center">
      <div className="text-6xl mb-4">💰</div>
      <h3 className="text-2xl font-bold mb-4">MONEY</h3>
      <p className="text-4xl font-bold text-blue-600 mb-2">$180K+</p>
      <p className="text-gray-600">in labor</p>
      <p className="text-2xl font-bold text-blue-600 mt-4">$90K/year</p>
      <p className="text-gray-600">maintenance</p>
    </div>

    {/* OPPORTUNITY CARD */}
    <div className="bg-white p-8 rounded-lg shadow-lg text-center">
      <div className="text-6xl mb-4">🚀</div>
      <h3 className="text-2xl font-bold mb-4">OPPORTUNITY</h3>
      <p className="text-4xl font-bold text-blue-600 mb-2">10-15</p>
      <p className="text-gray-600">features not shipped</p>
      <p className="text-gray-600 mt-4">Competitors ship first</p>
    </div>
  </div>
</section>
```

---

### **4. MODULE SHOWCASE - CRITICAL**

#### **Required: Grid of 6 Module Cards**

This is essential - must show all modules:

```jsx
<section className="py-16 bg-gray-50">
  <h2 className="text-4xl font-bold text-center mb-4">
    The Complete Ecosystem
  </h2>
  <p className="text-xl text-gray-600 text-center mb-12">
    Choose the modules you need. Skip what you don't.
  </p>
  
  <div className="max-w-6xl mx-auto grid md:grid-cols-2 lg:grid-cols-3 gap-6 px-4">
    {[
      {
        icon: "🧠",
        title: "AI Core",
        description: "Embeddings, Search, RAG, LLM Integration",
        link: "#"
      },
      {
        icon: "📊",
        title: "Behavior Analytics",
        description: "Churn Prediction, Sentiment Analysis",
        link: "#"
      },
      {
        icon: "🔄",
        title: "Migration Module",
        description: "Bulk Indexing with Pause/Resume",
        link: "#"
      },
      {
        icon: "🗣️",
        title: "Relationship Query",
        description: "Natural Language to SQL",
        link: "#"
      },
      {
        icon: "🌐",
        title: "Web Module",
        description: "59 REST Endpoints",
        link: "#"
      },
      {
        icon: "⚡",
        title: "ONNX Provider",
        description: "Free Local Embeddings",
        link: "#"
      }
    ].map((module) => (
      <div 
        key={module.title}
        className="bg-white p-6 rounded-lg shadow-lg hover:shadow-xl hover:-translate-y-1 transition-all cursor-pointer"
      >
        <div className="text-5xl mb-4">{module.icon}</div>
        <h3 className="text-xl font-bold mb-2">{module.title}</h3>
        <p className="text-gray-600 mb-4">{module.description}</p>
        <a href={module.link} className="text-blue-600 hover:text-blue-800">
          Learn More →
        </a>
      </div>
    ))}
  </div>
</section>
```

---

### **5. REGISTRATION FORM - CRITICAL**

#### **Current Issues:**
- May not have all 10 module checkboxes
- Missing validation messages
- Missing success state
- API integration may not be complete

#### **Required Complete Form:**

```typescript
// components/RegistrationForm.tsx

'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';

const moduleOptions = [
  { 
    id: 'ai-core', 
    label: 'AI Core', 
    description: 'Embeddings, Search, RAG, LLM Integration' 
  },
  { 
    id: 'behavior-analytics', 
    label: 'Behavior Analytics', 
    description: 'Churn Prediction, Sentiment Analysis' 
  },
  { 
    id: 'migration', 
    label: 'Migration Module', 
    description: 'Bulk Indexing with Pause/Resume' 
  },
  { 
    id: 'relationship-query', 
    label: 'Relationship Query', 
    description: 'Natural Language to SQL' 
  },
  { 
    id: 'web-module', 
    label: 'Web Module', 
    description: '59 REST Endpoints' 
  },
  { 
    id: 'onnx-provider', 
    label: 'ONNX Provider', 
    description: 'Free Local Embeddings' 
  },
  { 
    id: 'openai-provider', 
    label: 'OpenAI Provider', 
    description: 'OpenAI Integration' 
  },
  { 
    id: 'anthropic-provider', 
    label: 'Anthropic Provider', 
    description: 'Claude Integration' 
  },
  { 
    id: 'vector-databases', 
    label: 'Vector Databases', 
    description: 'Lucene, Milvus, Qdrant, etc.' 
  },
  { 
    id: 'all', 
    label: 'All Modules', 
    description: 'Everything!' 
  },
];

const formSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  modules: z.array(z.string()).min(1, 'Please select at least one module'),
});

type FormData = z.infer<typeof formSchema>;

export default function RegistrationForm() {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const { 
    register, 
    handleSubmit, 
    formState: { errors } 
  } = useForm<FormData>({
    resolver: zodResolver(formSchema),
  });

  const onSubmit = async (data: FormData) => {
    setIsSubmitting(true);
    setErrorMessage('');
    
    try {
      const response = await fetch('/api/register-interest', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...data,
          timestamp: new Date().toISOString(),
          source: 'landing-page',
        }),
      });

      const result = await response.json();

      if (response.ok && result.success) {
        setIsSuccess(true);
        // Optional: Track conversion event
        if (typeof window !== 'undefined' && window.gtag) {
          window.gtag('event', 'registration_complete', {
            event_category: 'engagement',
          });
        }
      } else {
        setErrorMessage(result.message || 'Something went wrong. Please try again.');
      }
    } catch (error) {
      setErrorMessage('Network error. Please check your connection and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isSuccess) {
    return (
      <div className="max-w-2xl mx-auto p-8 bg-gradient-to-br from-green-50 to-blue-50 rounded-lg shadow-xl text-center">
        <div className="text-8xl mb-6">🎉</div>
        <h3 className="text-3xl font-bold mb-4 text-green-600">
          Thank You!
        </h3>
        <p className="text-xl text-gray-700 mb-4">
          You're registered for AI Fabric Framework updates.
        </p>
        <p className="text-gray-600">
          We'll notify you about the 2026 Q2 launch and beta program opportunities.
        </p>
        <div className="mt-8 flex justify-center gap-4">
          <a 
            href="https://github.com/your-org/ai-fabric-framework"
            className="px-6 py-3 bg-gray-900 text-white rounded-lg hover:bg-gray-800"
            target="_blank"
            rel="noopener noreferrer"
          >
            ⭐ Star on GitHub
          </a>
          <a 
            href="https://aifabric.dev"
            className="px-6 py-3 border-2 border-gray-900 text-gray-900 rounded-lg hover:bg-gray-50"
          >
            Visit Website
          </a>
        </div>
      </div>
    );
  }

  return (
    <form 
      onSubmit={handleSubmit(onSubmit)} 
      className="max-w-2xl mx-auto p-8 bg-white rounded-lg shadow-xl"
    >
      <h2 className="text-3xl font-bold mb-2">Get Early Access</h2>
      <p className="text-gray-600 mb-8">
        Register your interest for updates and priority access to Pro License
      </p>

      {errorMessage && (
        <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
          <p className="text-red-600">{errorMessage}</p>
        </div>
      )}

      {/* Email Field */}
      <div className="mb-6">
        <label className="block text-sm font-semibold mb-2 text-gray-700">
          📧 Email Address *
        </label>
        <input
          type="email"
          {...register('email')}
          className={`w-full px-4 py-3 border-2 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition ${
            errors.email ? 'border-red-500' : 'border-gray-300'
          }`}
          placeholder="developer@example.com"
          disabled={isSubmitting}
        />
        {errors.email && (
          <p className="text-red-500 text-sm mt-2 flex items-center">
            <span className="mr-1">⚠️</span>
            {errors.email.message}
          </p>
        )}
      </div>

      {/* Modules Multi-Select */}
      <div className="mb-8">
        <label className="block text-sm font-semibold mb-3 text-gray-700">
          📦 Which modules interest you? * (Select all that apply)
        </label>
        <div className="space-y-3">
          {moduleOptions.map((module) => (
            <label 
              key={module.id} 
              className={`flex items-start space-x-3 p-4 border-2 rounded-lg cursor-pointer transition ${
                errors.modules 
                  ? 'border-red-300 bg-red-50' 
                  : 'border-gray-200 hover:border-blue-300 hover:bg-blue-50'
              }`}
            >
              <input
                type="checkbox"
                value={module.id}
                {...register('modules')}
                className="mt-1 w-5 h-5 text-blue-600 border-gray-300 rounded focus:ring-2 focus:ring-blue-500"
                disabled={isSubmitting}
              />
              <div className="flex-1">
                <div className="font-semibold text-gray-900">{module.label}</div>
                <div className="text-sm text-gray-600">{module.description}</div>
              </div>
            </label>
          ))}
        </div>
        {errors.modules && (
          <p className="text-red-500 text-sm mt-2 flex items-center">
            <span className="mr-1">⚠️</span>
            {errors.modules.message}
          </p>
        )}
      </div>

      {/* Submit Button */}
      <button
        type="submit"
        disabled={isSubmitting}
        className="w-full py-4 bg-gradient-to-r from-blue-600 to-purple-600 text-white text-lg font-bold rounded-lg hover:from-blue-700 hover:to-purple-700 hover:shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {isSubmitting ? (
          <span className="flex items-center justify-center">
            <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            Registering...
          </span>
        ) : (
          'Register Interest'
        )}
      </button>

      <div className="mt-4 text-center">
        <p className="text-xs text-gray-500">
          ✓ No spam, just updates • ✓ Unsubscribe anytime • ✓ We respect your privacy
        </p>
      </div>
    </form>
  );
}
```

---

### **6. TIMELINE SECTION - ADD THIS**

```jsx
<section className="py-16 bg-gradient-to-br from-blue-50 to-purple-50">
  <h2 className="text-4xl font-bold text-center mb-12">
    🗓️ The Road to AI Fabric 1.0
  </h2>
  
  <div className="max-w-4xl mx-auto px-4">
    <div className="space-y-8">
      {/* 2024 */}
      <div className="flex gap-6">
        <div className="flex-shrink-0 w-32">
          <div className="bg-green-500 text-white px-4 py-2 rounded-lg text-center font-bold">
            2024
          </div>
          <div className="text-center text-sm text-gray-600 mt-2">
            ✅ Foundation
          </div>
        </div>
        <div className="flex-1 bg-white p-6 rounded-lg shadow-lg">
          <h3 className="font-bold text-lg mb-3">Current Phase</h3>
          <ul className="space-y-2 text-gray-700">
            <li>✅ Core architecture design</li>
            <li>✅ Modular provider system</li>
            <li>✅ Annotation framework</li>
            <li>✅ Privacy-first design</li>
          </ul>
        </div>
      </div>

      {/* 2025 */}
      <div className="flex gap-6">
        <div className="flex-shrink-0 w-32">
          <div className="bg-blue-500 text-white px-4 py-2 rounded-lg text-center font-bold">
            2025
          </div>
          <div className="text-center text-sm text-gray-600 mt-2">
            🔄 Testing
          </div>
        </div>
        <div className="flex-1 bg-white p-6 rounded-lg shadow-lg">
          <h3 className="font-bold text-lg mb-3">Production Validation</h3>
          <ul className="space-y-2 text-gray-700">
            <li>🔄 Real-world validation</li>
            <li>🔄 Beta program launch</li>
            <li>🔄 Performance tuning</li>
            <li>🔄 Documentation suite</li>
          </ul>
        </div>
      </div>

      {/* 2026 */}
      <div className="flex gap-6">
        <div className="flex-shrink-0 w-32">
          <div className="bg-purple-600 text-white px-4 py-2 rounded-lg text-center font-bold">
            2026 Q2
          </div>
          <div className="text-center text-sm text-gray-600 mt-2">
            🎯 Launch
          </div>
        </div>
        <div className="flex-1 bg-gradient-to-br from-purple-50 to-blue-50 p-6 rounded-lg shadow-lg border-2 border-purple-300">
          <h3 className="font-bold text-lg mb-3">🚀 v1.0 Release</h3>
          <ul className="space-y-2 text-gray-700">
            <li>🎯 General Availability</li>
            <li>🎯 Full documentation</li>
            <li>🎯 Pro License available</li>
            <li>🎯 Production support</li>
          </ul>
        </div>
      </div>
    </div>
    
    <div className="mt-12 text-center">
      <p className="text-xl text-gray-700 mb-6">
        <strong>Why 2026?</strong> We're not rushing this. AI infrastructure is too important to get wrong.
      </p>
      <button className="px-8 py-4 bg-gradient-to-r from-blue-600 to-purple-600 text-white text-lg font-bold rounded-lg hover:shadow-lg">
        Register for 2026 Launch
      </button>
    </div>
  </div>
</section>
```

---

### **7. CODE EXAMPLE SECTION**

#### **Required: Tabbed Interface Showing 3 Steps**

```jsx
<section className="py-16">
  <h2 className="text-4xl font-bold text-center mb-12">
    From Zero to AI in 3 Steps
  </h2>
  
  <div className="max-w-4xl mx-auto px-4">
    <Tabs defaultValue="step1">
      <TabsList className="grid w-full grid-cols-3 mb-8">
        <TabsTrigger value="step1">1. Add Dependency</TabsTrigger>
        <TabsTrigger value="step2">2. Annotate Entity</TabsTrigger>
        <TabsTrigger value="step3">3. Search</TabsTrigger>
      </TabsList>
      
      <TabsContent value="step1">
        <pre className="bg-gray-900 text-white p-6 rounded-lg overflow-x-auto">
          <code>{`<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-core</artifactId>
    <version>1.0.0</version>
</dependency>`}</code>
        </pre>
      </TabsContent>
      
      <TabsContent value="step2">
        <pre className="bg-gray-900 text-white p-6 rounded-lg overflow-x-auto">
          <code>{`@Entity
@AICapable(entityType = "product")
public class Product {
    @Id private UUID id;
    private String name;
    private String description;
}`}</code>
        </pre>
      </TabsContent>
      
      <TabsContent value="step3">
        <pre className="bg-gray-900 text-white p-6 rounded-lg overflow-x-auto">
          <code>{`@Autowired
private AISearchService searchService;

AISearchResponse results = 
    searchService.search("laptop for developers");

// Returns: MacBook Pro, ThinkPad, Dell XPS
// (not laptop bags and stands)`}</code>
        </pre>
      </TabsContent>
    </Tabs>
    
    <p className="text-center text-2xl font-bold mt-8">
      That's it. Really. ✨
    </p>
  </div>
</section>
```

---

### **8. FOOTER - ENHANCE**

```jsx
<footer className="bg-gray-900 text-white py-12">
  <div className="max-w-6xl mx-auto px-4">
    <div className="grid md:grid-cols-4 gap-8 mb-8">
      {/* Brand */}
      <div>
        <div className="text-2xl font-bold mb-4">AI Fabric</div>
        <p className="text-gray-400 text-sm">
          Making AI accessible to every developer
        </p>
        <div className="mt-4">
          <p className="text-sm text-gray-400">📅 Target Launch: Q2 2026</p>
        </div>
      </div>
      
      {/* Documentation */}
      <div>
        <h3 className="font-bold mb-4">Documentation</h3>
        <ul className="space-y-2 text-sm text-gray-400">
          <li><a href="#" className="hover:text-white">Getting Started</a></li>
          <li><a href="https://github.com/your-org/ai-fabric-framework" className="hover:text-white">GitHub</a></li>
          <li><a href="#" className="hover:text-white">Roadmap</a></li>
          <li><a href="#" className="hover:text-white">Blog</a></li>
        </ul>
      </div>
      
      {/* Community */}
      <div>
        <h3 className="font-bold mb-4">Community</h3>
        <ul className="space-y-2 text-sm text-gray-400">
          <li><a href="#" className="hover:text-white">Discord</a></li>
          <li><a href="#" className="hover:text-white">Twitter</a></li>
          <li><a href="#" className="hover:text-white">Discussions</a></li>
        </ul>
      </div>
      
      {/* Legal */}
      <div>
        <h3 className="font-bold mb-4">Legal</h3>
        <ul className="space-y-2 text-sm text-gray-400">
          <li><a href="#" className="hover:text-white">Privacy Policy</a></li>
          <li><a href="#" className="hover:text-white">Terms of Service</a></li>
          <li><a href="#" className="hover:text-white">MIT License</a></li>
        </ul>
      </div>
    </div>
    
    <div className="border-t border-gray-800 pt-8 text-center text-sm text-gray-400">
      <p>© 2024 AI Fabric Framework</p>
      <p className="mt-2">Built with ❤️ for the developer community</p>
    </div>
  </div>
</footer>
```

---

### **9. MOBILE RESPONSIVENESS**

Ensure all sections stack properly on mobile:

```css
/* Critical responsive fixes */

@media (max-width: 768px) {
  /* Hero */
  .hero h1 {
    font-size: 2rem !important;
    line-height: 1.2;
  }
  
  .hero .code-block {
    font-size: 0.875rem;
    overflow-x: auto;
  }
  
  /* Module grid - single column on mobile */
  .module-grid {
    grid-template-columns: 1fr !important;
  }
  
  /* Form checkboxes - larger touch targets */
  .form-checkbox {
    min-height: 60px;
    padding: 1rem !important;
  }
  
  /* CTA buttons - full width */
  .cta-button {
    width: 100% !important;
    padding: 1rem !important;
  }
}
```

---

### **10. SEO & META TAGS**

Add to `<head>`:

```html
<head>
  <title>AI Fabric: Spring Boot AI Framework | 2026 Launch</title>
  <meta name="description" content="AI Fabric makes adding AI to Spring Boot apps as easy as adding Spring Security. Semantic search, embeddings, and RAG with one annotation. Launching Q2 2026." />
  <meta name="keywords" content="AI Fabric, Spring Boot AI, semantic search framework, AI infrastructure, Java AI framework" />
  
  <!-- OpenGraph -->
  <meta property="og:title" content="AI Fabric: Stop Building AI Infrastructure" />
  <meta property="og:description" content="Add AI to Spring Boot in 5 minutes instead of 5 months. Open source framework launching Q2 2026." />
  <meta property="og:image" content="https://aifabric.lovable.app/og-image.png" />
  <meta property="og:url" content="https://aifabric.lovable.app" />
  <meta property="og:type" content="website" />
  
  <!-- Twitter -->
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="AI Fabric: Spring Boot AI Framework" />
  <meta name="twitter:description" content="5 months → 5 minutes. AI infrastructure for Spring Boot. Launching 2026." />
  <meta name="twitter:image" content="https://aifabric.lovable.app/twitter-card.png" />
  
  <!-- Favicon -->
  <link rel="icon" type="image/png" href="/favicon.png" />
</head>
```

---

### **11. PERFORMANCE OPTIMIZATIONS**

```javascript
// Add to your build config or component

// 1. Lazy load form component
const RegistrationForm = dynamic(() => import('./RegistrationForm'), {
  loading: () => <p>Loading form...</p>,
  ssr: false
});

// 2. Optimize images
// Replace all <img> with Next.js <Image> component
import Image from 'next/image';

// 3. Add loading states
// Show skeleton loaders while content loads

// 4. Preload critical resources
<link rel="preload" href="/fonts/inter.woff2" as="font" type="font/woff2" crossOrigin="anonymous" />
```

---

### **12. ANALYTICS INTEGRATION**

Add to track conversions:

```javascript
// app/layout.tsx or _app.tsx

export default function RootLayout({ children }) {
  return (
    <html>
      <head>
        {/* Google Analytics */}
        <script async src="https://www.googletagmanager.com/gtag/js?id=GA_MEASUREMENT_ID"></script>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              window.dataLayer = window.dataLayer || [];
              function gtag(){dataLayer.push(arguments);}
              gtag('js', new Date());
              gtag('config', 'GA_MEASUREMENT_ID');
            `,
          }}
        />
      </head>
      <body>{children}</body>
    </html>
  );
}

// Track events in form:
gtag('event', 'registration_complete', {
  event_category: 'engagement',
  event_label: 'email_registration'
});
```

---

## 🎯 PRIORITY ORDER

### **Must Have (Do First):**
1. ✅ Fix registration form - all 10 checkboxes
2. ✅ Add timeline section (2024→2025→2026)
3. ✅ Add module showcase grid (6 cards)
4. ✅ Add problem comparison section
5. ✅ Update hero with animated code

### **Should Have (Do Second):**
6. ✅ Add cost breakdown section
7. ✅ Add code tabs section
8. ✅ Enhance footer
9. ✅ Mobile responsive fixes
10. ✅ SEO meta tags

### **Nice to Have (Polish):**
11. Add animations with Framer Motion
12. Add confetti on form success
13. Add live GitHub star count
14. Add dark mode toggle

---

## 🧪 TESTING CHECKLIST

After making changes, test:

- [ ] Form submits successfully
- [ ] All 10 checkboxes work
- [ ] Email validation works
- [ ] Success state shows
- [ ] Mobile layout looks good
- [ ] All links work (GitHub, etc.)
- [ ] Timeline section displays correctly
- [ ] Module cards hover effects work
- [ ] Page loads in < 2 seconds
- [ ] No console errors

---

## 📝 QUICK REFERENCE

**Current URL:** https://aifabric.lovable.app/

**Required Sections (In Order):**
1. Hero with animated code
2. Problem comparison (Old Way vs New Way)
3. Cost breakdown (Time, Money, Opportunity)
4. Module showcase (6 cards)
5. How it works (3-step code tabs)
6. Timeline roadmap (2024→2025→2026)
7. Registration form (email + 10 checkboxes)
8. Footer

**Key Messages:**
- "5 months → 5 minutes"
- "Stop building AI infrastructure"
- "Launching Q2 2026"
- "Open source forever"

---

## 🚀 HOW TO USE THIS PROMPT

Copy specific sections above and paste them into Lovable's chat to make targeted improvements:

Example:
```
"Add the Timeline section (2024→2025→2026) as shown in section 6 of the diff prompt"
```

or

```
"Update the registration form to include all 10 module checkboxes with the exact implementation from section 5"
```

This will give Lovable precise instructions on what to add/change!
