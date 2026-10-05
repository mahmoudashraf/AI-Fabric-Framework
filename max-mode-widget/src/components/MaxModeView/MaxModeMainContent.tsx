import { DesktopContextPanel } from "../DesktopContextPanel";
import { MessageList } from "../Chat/MessageList";
import { MobileContextSheet } from "../MobileContextSheet";
import { MobileToolRail } from "../MobileToolRail";
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
    debugEnabled,
    openDebugInspector,
    resendChatQuery,
    reattachItemWithToast,
    openSourcesMobile,
    openSourcesDesktop,
    expandActionResults,
    handleConfirmation,
    isItemAttached,
    handleAttachActionResultItem,
    connectCustomerAccount,
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
    cartEnabled,
    handleAttachDocument,
    isFloatingMenuCollapsed,
    setIsFloatingMenuCollapsed,
    isNewDocsPreviewOpen,
    handleCloseNewDocsPreview,
    isBottomSheetOpen,
    setIsBottomSheetOpen,
    handleOpenBottomSheet,
    setIsQuickActionsOpen,
    toolRailItems,
    activeToolScope,
    contextualToolsAvailable,
    selectToolScope,
    handleQuickAction,
    proceedToCheckoutFromCart,
    attachCartToChat,
    browseProductsFromCart,
  } = controller;

  return (
    <div className="h-full relative bg-white dark:bg-gray-950">
      <MessageList
        containerClassName={`absolute top-0 ${controller.toolGroups.length > 0 ? "md:top-[120px]" : "md:top-[72px]"} left-0 right-0 bottom-0 overflow-y-auto px-3 md:px-6 py-4 md:py-6 transition-all ${isPanelVisible && contextDocuments.length > 0 ? (selectedProduct || isCartView ? "md:pr-[730px]" : "md:pr-[450px]") : "md:pr-4"} ${debugEnabled && controller.isDebugModalOpen ? "xl:pl-[420px]" : ""}`}
        containerStyle={{ paddingBottom: "var(--max-mode-composer-inset, 180px)" }}
        messages={chatMessages}
        latestMessageRef={latestMessageRef}
        messagesEndRef={messagesEndRef}
        isLoading={isLoading}
        getAiStyles={getResultStyles}
        isPanelVisible={isPanelVisible}
        attachedItems={attachedItems}
        confirmationStatus={confirmationStatus as Record<string, "confirmed" | "rejected">}
        expandedActions={expandedActions}
        debugEnabled={debugEnabled}
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
        onPresentationAsk={controller.handleActionPresentationAsk}
        onAttachPresentationResult={controller.handleAttachPresentationResult}
        onDetachPresentationResult={controller.handleDetachPresentationResult}
        isPresentationResultAttached={controller.isPresentationResultAttached}
        onCustomerAccountConnect={connectCustomerAccount}
        onClarificationSubmit={controller.handleClarificationSubmit}
        contentClassName="max-w-6xl"
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
        cartEnabled={cartEnabled}
        onAddToCart={(product) => {
          void addToCart(product);
        }}
        onAttachProductToChat={(product) => {
          handleAttachDocument(product);
          closeProductDetails();
        }}
        onAttachDocument={handleAttachDocument}
      />

      <MobileToolRail
        items={toolRailItems}
        activeToolScope={activeToolScope}
        contextualToolsAvailable={contextualToolsAvailable}
        contextDocumentsCount={contextDocuments.length}
        collapsed={isFloatingMenuCollapsed}
        onCollapsedChange={setIsFloatingMenuCollapsed}
        onOpenTools={(scope) => {
          selectToolScope(scope);
          setIsQuickActionsOpen(true);
        }}
        onOpenDocuments={handleOpenBottomSheet}
        onPrompt={(item) => {
          if (item.query) handleQuickAction(item.query, item.position, item.mode);
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
        cartEnabled={cartEnabled}
        onAddToCart={(product) => {
          void addToCart(product);
        }}
        onAttachProductToChat={handleAttachDocument}
        onAttachDocument={handleAttachDocument}
      />
    </div>
  );
}
