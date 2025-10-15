import type { Metadata } from 'next';

import './globals.css';

// PROJECT IMPORTS
// Modern Context API Provider - Redux eliminated
import { ReactQueryProvider } from 'providers/ReactQueryProvider';
import { ConfigProvider } from 'contexts/ConfigContext';
import { AuthProviderWrapper } from 'contexts/AuthProviderWrapper';
import { NotificationProvider } from 'contexts/NotificationContext';

// All modern Context providers
import { KanbanProvider } from 'contexts/KanbanContext';
import { CalendarProvider } from 'contexts/CalendarContext';
import { ChatProvider } from 'contexts/ChatContext';
import { ContactProvider } from 'contexts/ContactContext';
import { CustomerProvider } from 'contexts/CustomerContext';
import { MailProvider } from 'contexts/MailContext';
import { ProductProvider } from 'contexts/ProductContext';
import { UserProvider } from 'contexts/UserContext';
import { MenuProvider } from 'contexts/MenuContext';
import { CartProvider } from 'contexts/CartContext';

// UI components
import NavigationScroll from 'layout/NavigationScroll';
import MigrationThemeWrapper from 'layout/MigrationThemeWrapper';
// Note: ThemeCustomization handled by MigrationThemeWrapper
import SnackbarWrapper from 'ui-component/extended/SnackbarWrapper';
import Locales from 'ui-component/Locales';
import RTLLayout from 'ui-component/RTLLayout';
import Notistack from 'ui-component/third-party/Notistack';

// Complete Context Implementation - All PROVIDERS
const AllContextProviders = ({ children }: { children: React.ReactNode }) => (
  <MenuProvider>
    <CartProvider>
      <KanbanProvider>
        <CalendarProvider>
          <ChatProvider>
            <ContactProvider>
              <CustomerProvider>
                <MailProvider>
                  <ProductProvider>
                    <UserProvider>
                      {children}
                    </UserProvider>
                  </ProductProvider>
                </MailProvider>
              </CustomerProvider>
            </ContactProvider>
          </ChatProvider>
        </CalendarProvider>
      </KanbanProvider>
    </CartProvider>
  </MenuProvider>
);

const ProviderWrapper = ({ children }: { children: React.ReactNode }) => (
  <ReactQueryProvider>
    <ConfigProvider>
      <MigrationThemeWrapper>
        <RTLLayout>
          <Locales>
            <NavigationScroll>
              <AuthProviderWrapper>
                <NotificationProvider>
                  <Notistack>
                    <SnackbarWrapper />
                    <AllContextProviders>
                      {children}
                    </AllContextProviders>
                  </Notistack>
                </NotificationProvider>
              </AuthProviderWrapper>
            </NavigationScroll>
          </Locales>
        </RTLLayout>
      </MigrationThemeWrapper>
    </ConfigProvider>
  </ReactQueryProvider>
);

export const metadata: Metadata = {
  title: 'Kiwi - React Material Admin Dashboard Template by CodedThemes',
  description:
    'Start your next React project with Kiwi admin template. It build with Reactjs, Material-UI, Redux, and Hook for faster web development.',
};

// ==============================|| ROOT LAYOUT ||============================== //

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <ProviderWrapper>{children}</ProviderWrapper>
      </body>
    </html>
  );
}
