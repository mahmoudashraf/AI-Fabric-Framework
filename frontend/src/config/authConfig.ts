/**
 * Mock Authentication Configuration
 * This service handles environment-specific authentication configuration
 */

export interface AuthConfig {
  apiUrl: string;
  mockAuthEnabled: boolean;
  environment: string;
  mockUsersEndpoint: string;
  mockAuthEndpoint: string;
  supabaseUrl?: string;
  supabaseAnonKey?: string;
  enableMockUserTester: boolean;
  debugMode: boolean;
}

class AuthConfigService {
  private config: AuthConfig;

  constructor() {
    this.config = this.loadConfig();
  }

  private loadConfig(): AuthConfig {
    const config: AuthConfig = {
      apiUrl: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
      mockAuthEnabled: process.env.NEXT_PUBLIC_MOCK_AUTH_ENABLED === 'true',
      environment: process.env.NEXT_PUBLIC_ENVIRONMENT || 'development',
      mockUsersEndpoint: process.env.NEXT_PUBLIC_MOCK_USERS_ENDPOINT || '/mock/users',
      mockAuthEndpoint: process.env.NEXT_PUBLIC_MOCK_AUTH_ENDPOINT || '/mock/auth',
      enableMockUserTester: process.env.NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER === 'true',
      debugMode: process.env.NEXT_PUBLIC_DEBUG_MODE === 'true',
    };
    
    // Only add optional properties if they exist
    if (process.env.NEXT_PUBLIC_SUPABASE_URL) {
      config.supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
    }
    if (process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY) {
      config.supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
    }
    
    return config;
  }

  public getConfig(): AuthConfig {
    return this.config;
  }

  public isMockAuthEnabled(): boolean {
    return this.config.mockAuthEnabled && this.config.environment === 'development';
  }

  public isDevelopment(): boolean {
    return this.config.environment === 'development';
  }

  public isProduction(): boolean {
    return this.config.environment === 'production';
  }

  public getApiUrl(): string {
    return this.config.apiUrl;
  }

  public getMockAuthUrl(): string {
    return `${this.config.apiUrl}${this.config.mockAuthEndpoint}`;
  }

  public getMockUsersUrl(): string {
    return `${this.config.apiUrl}${this.config.mockUsersEndpoint}`;
  }

  public shouldUseMockAuth(): boolean {
    return this.isMockAuthEnabled() && this.isDevelopment();
  }

  public log(message: string, ...args: any[]): void {
    if (this.config.debugMode) {
      console.log(`[AuthConfig] ${message}`, ...args);
    }
  }
}

// Export singleton instance
export const authConfig = new AuthConfigService();

// Export default config for easy access
export default authConfig.getConfig();
