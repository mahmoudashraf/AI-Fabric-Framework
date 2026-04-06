import { DesktopContextPanel } from "../DesktopContextPanel";
import { MessageList } from "../Chat/MessageList";
import { MobileContextSheet } from "../MobileContextSheet";
import { MobileFloatingActions } from "../MobileFloatingActions";
import { MobileNewDocsPreviewPanel } from "../MobileNewDocsPreviewPanel";

import type { MaxModeController } from "@/hooks/useMaxModeController";

export function MaxModeMainContent({ controller }: { controller: MaxModeController }) {
  const {
    isPanelVisible,
    setIsPanelVisible,
    contextDocuments,
    selectedProduct,
    isCartView,
    chatMessages,
    latestMessageRef,
    messagesEndRef,
    isLoading,
    getResultStyles,
    attachedItems,
    confirmationStatus,
    expandedActions,
    openDebugInspector,
    resendChatQuery,
    reattachItemWithToast,
    openSourcesMobile,
    openSourcesDesktop,
    expandActionResults,
    handleConfirmation,
    isItemAttached,
    handleAttachActionResultItem,
    contextPanelRef,
    contextPanelEndRef,
    cartData,
    newDocuments,
    viewedDocumentIds,
    openProductDetails,
    closeCart,
    closeProductDetails,
    removeFromCart,
    addToCart,
    handleAttachDocument,
    isAISearchOpen,
    setIsAISearchOpen,
    aiSearchCategories,
    aiSearchRowRef,
    aiSearchButtonRef,
    handleAISearchCategory,
    isFloatingMenuCollapsed,
    setIsFloatingMenuCollapsed,
    openCart,
    isNewDocsPreviewOpen,
    handleCloseNewDocsPreview,
    isBottomSheetOpen,
    setIsBottomSheetOpen,
    handleOpenBottomSheet,
    isQuickActionsOpen,
    setIsQuickActionsOpen,
    isBrowseProductsOpen,
    setIsBrowseProductsOpen,
    browseProductCategories,
    handleQuickAction,
    proceedToCheckoutFromCart,
    attachCartToChat,
    browseProductsFromCart,
  } = controller;

  return (
    <div className="h-full relative">
      <MessageList
        containerClassName={`absolute top-0 md:top-[72px] left-0 right-0 bottom-0 overflow-y-auto px-3 md:px-6 py-4 md:py-6 pb-[180px] md:pb-[240px] transition-all ${isPanelVisible && contextDocuments.length > 0 ? (selectedProduct || isCartView ? "md:pr-[730px]" : "md:pr-[450px]") : "md:pr-4"} ${controller.isDebugModalOpen ? "xl:pl-[420px]" : ""}`}
        messages={chatMessages}
        latestMessageRef={latestMessageRef}
        messagesEndRef={messagesEndRef}
        isLoading={isLoading}
        getAiStyles={getResultStyles}
        isPanelVisible={isPanelVisible}
        attachedItems={attachedItems}
        confirmationStatus={confirmationStatus as Record<string, "confirmed" | "rejected">}
        expandedActions={expandedActions}
        onOpenDebug={openDebugInspector}
        onResendAction={(fullMessage) => {
          void resendChatQuery(fullMessage);
        }}
        onReattachItem={reattachItemWithToast}
        onOpenSourcesMobile={openSourcesMobile}
        onOpenSourcesDesktop={openSourcesDesktop}
        onConfirm={(messageId, confirmed, msg) => handleConfirmation(messageId, confirmed, msg)}
        onExpandActionResults={expandActionResults}
        isItemAttached={isItemAttached}
        onAttachActionResultItem={handleAttachActionResultItem}
        onNextStepClick={(query) => {
          void resendChatQuery(query);
        }}
        onClarificationSubmit={controller.handleClarificationSubmit}
      />

      <DesktopContextPanel
        contextDocuments={contextDocuments}
        isPanelVisible={isPanelVisible}
        setIsPanelVisible={setIsPanelVisible}
        selectedProduct={selectedProduct}
        isCartView={isCartView}
        cartData={cartData}
        focusedMessageId={controller.focusedMessageId}
        newDocuments={newDocuments}
        viewedDocumentIds={viewedDocumentIds}
        contextPanelRef={contextPanelRef}
        contextPanelEndRef={contextPanelEndRef}
        isItemAttached={isItemAttached}
        onOpenProductDetails={openProductDetails}
        onCloseCart={closeCart}
        onCloseProductDetails={closeProductDetails}
        onRemoveFromCart={(sku) => {
          void removeFromCart(sku);
        }}
        onProceedToCheckout={() => {
          void proceedToCheckoutFromCart({ closeCartAfter: true });
        }}
        onAttachCartToChat={attachCartToChat}
        onBrowseProducts={() => {
          void browseProductsFromCart();
        }}
        onAddToCart={(product) => {
          void addToCart(product);
        }}
        onAttachProductToChat={(product) => {
          handleAttachDocument(product);
          closeProductDetails();
        }}
        onAttachDocument={handleAttachDocument}
      />

      <MobileFloatingActions
        isAISearchOpen={isAISearchOpen}
        setIsAISearchOpen={setIsAISearchOpen}
        aiSearchCategories={aiSearchCategories}
        aiSearchRowRef={aiSearchRowRef}
        aiSearchButtonRef={aiSearchButtonRef}
        onAISearchCategory={handleAISearchCategory}
        isFloatingMenuCollapsed={isFloatingMenuCollapsed}
        setIsFloatingMenuCollapsed={setIsFloatingMenuCollapsed}
        contextDocumentsCount={contextDocuments.length}
        onOpenDocuments={handleOpenBottomSheet}
        onOpenCart={openCart}
        isQuickActionsOpen={isQuickActionsOpen}
        setIsQuickActionsOpen={setIsQuickActionsOpen}
        isBrowseProductsOpen={isBrowseProductsOpen}
        setIsBrowseProductsOpen={setIsBrowseProductsOpen}
        browseProductCategories={browseProductCategories}
        onBrowseProductCategory={(category) => {
          handleQuickAction(category.query, "search", "navigator");
        }}
      />

      <MobileNewDocsPreviewPanel
        isOpen={isNewDocsPreviewOpen}
        newDocuments={newDocuments}
        onClose={handleCloseNewDocsPreview}
        onSelectDocument={(doc) => {
          openProductDetails(doc);
          handleCloseNewDocsPreview();
          setIsBottomSheetOpen(true);
        }}
        onViewAll={() => {
          handleCloseNewDocsPreview();
          handleOpenBottomSheet();
        }}
      />

      <MobileContextSheet
        isOpen={isBottomSheetOpen}
        setIsOpen={setIsBottomSheetOpen}
        contextDocuments={contextDocuments}
        selectedProduct={selectedProduct}
        isCartView={isCartView}
        cartData={cartData}
        viewedDocumentIds={viewedDocumentIds}
        newDocuments={newDocuments}
        isItemAttached={isItemAttached}
        onCloseAll={() => {
          setIsBottomSheetOpen(false);
          closeProductDetails();
          if (isCartView) closeCart();
        }}
        onCloseCart={closeCart}
        onCloseProductDetails={closeProductDetails}
        onOpenProductDetails={openProductDetails}
        onRemoveFromCart={(sku) => {
          void removeFromCart(sku);
        }}
        onProceedToCheckout={() => {
          void proceedToCheckoutFromCart({ closeCartAfter: false });
        }}
        onAttachCartToChat={attachCartToChat}
        onBrowseProducts={() => {
          void browseProductsFromCart();
        }}
        onAddToCart={(product) => {
          void addToCart(product);
        }}
        onAttachProductToChat={handleAttachDocument}
        onAttachDocument={handleAttachDocument}
      />
    </div>
  );
}

