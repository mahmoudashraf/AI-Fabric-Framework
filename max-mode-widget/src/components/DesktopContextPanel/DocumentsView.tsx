import type { RefObject } from "react";

import { AnimatePresence, motion } from "framer-motion";
import { BrainCircuit, CheckCircle2, ChevronDown, ChevronUp, FileText, Image as ImageIcon, Package, Star } from "lucide-react";

import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";

import type { Document } from "@/types";
import { formatFieldName } from "@/utils";

const getDocumentIcon = (type: string) => {
  switch (type.toLowerCase()) {
    case "policy":
    case "document":
    case "warranty":
    case "terms":
      return FileText;
    case "product":
      return Package;
    case "review":
      return Star;
    case "image":
      return ImageIcon;
    default:
      return FileText;
  }
};

export function DocumentsView({
  contextDocuments,
  focusedMessageId,
  newDocuments,
  viewedDocumentIds,
  contextPanelRef,
  contextPanelEndRef,
  isItemAttached,
  onOpenProductDetails,
  onAttachDocument,
}: {
  contextDocuments: Document[];
  focusedMessageId: string | null;
  newDocuments: Document[];
  viewedDocumentIds: Set<string>;
  contextPanelRef: RefObject<HTMLDivElement>;
  contextPanelEndRef: RefObject<HTMLDivElement>;
  isItemAttached: (itemId: string) => boolean;
  onOpenProductDetails: (doc: Document) => void;
  onAttachDocument: (doc: Document) => void;
}) {
  const handleScrollUp = () => {
    contextPanelRef.current?.scrollBy({ top: -300, behavior: "smooth" });
  };

  const handleScrollDown = () => {
    contextPanelRef.current?.scrollBy({ top: 300, behavior: "smooth" });
  };

  return (
    <div className="flex-1 relative min-h-0">
      <Button
        variant="ghost"
        size="icon"
        onClick={handleScrollUp}
        className="hidden lg:flex absolute top-4 left-1/2 -translate-x-1/2 z-20 h-10 w-10 rounded-full bg-gradient-to-br from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white shadow-xl border-2 border-white/30 hover:scale-110 transition-all"
        title="Scroll Up"
      >
        <ChevronUp className="h-5 w-5" />
      </Button>

      <div
        ref={contextPanelRef}
        className="absolute inset-0 overflow-y-auto px-2 py-2 space-y-4"
        style={{
          scrollbarWidth: "thin",
          scrollbarColor: "rgba(168, 85, 247, 0.5) rgba(243, 232, 255, 0.2)",
        }}
      >
        <AnimatePresence mode="popLayout">
          {contextDocuments.map((doc, idx) => {
            const DocIcon = getDocumentIcon(doc.type);
            const isFocused = doc.messageId === focusedMessageId;
            const isNewDoc = !viewedDocumentIds.has(doc.id) && newDocuments.some((nd) => nd.id === doc.id);

            return (
              <motion.div
                key={doc.id}
                data-doc-message-id={doc.messageId}
                data-doc-id={doc.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                transition={{ delay: idx * 0.05 }}
              >
                <Card
                  onClick={() => onOpenProductDetails(doc)}
                  className={`relative group hover:shadow-2xl transition-all duration-300 border-2 cursor-pointer ${
                    isNewDoc
                      ? "border-yellow-400 shadow-lg shadow-yellow-200/50 bg-gradient-to-br from-yellow-50/90 via-blue-50/40 to-white/40"
                      : isFocused
                        ? "border-yellow-400 shadow-lg shadow-yellow-200/50 bg-gradient-to-br from-yellow-50 via-blue-50/50 to-white/50"
                        : "border-blue-300 hover:border-blue-500 bg-gradient-to-br from-white via-blue-50/50 to-white"
                  } dark:from-gray-800 dark:to-blue-900/20 overflow-hidden`}
                >
                  {doc.metadata?.imageUrl && (
                    <div className="relative h-48 overflow-hidden bg-gradient-to-br from-blue-100 to-white">
                      <img
                        src={doc.metadata.imageUrl}
                        alt={doc.title}
                        className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
                      />
                      <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
                      {isNewDoc && (
                        <div className="absolute top-2 left-2 bg-yellow-400 text-yellow-900 text-[10px] font-bold px-2 py-1 rounded-full shadow-lg z-10">
                          NEW
                        </div>
                      )}
                    </div>
                  )}

                  <Button
                    size="icon"
                    variant="ghost"
                    className={`absolute top-2 right-2 h-10 w-10 ${
                      isItemAttached(doc.id)
                        ? "bg-gradient-to-br from-green-500 to-emerald-500 hover:from-green-600 hover:to-emerald-600"
                        : "bg-gradient-to-br from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600"
                    } text-white shadow-xl border-2 border-white/50 hover:scale-110 hover:border-white/50 transition-all z-50 pointer-events-auto cursor-pointer`}
                    onClick={(e) => {
                      e.stopPropagation();
                      e.preventDefault();
                      onAttachDocument(doc);
                    }}
                    title={isItemAttached(doc.id) ? "Already in Chat" : "Attach to Chat"}
                  >
                    {isItemAttached(doc.id) ? <CheckCircle2 className="h-5 w-5" /> : <BrainCircuit className="h-5 w-5" />}
                  </Button>

                  <CardHeader className="pb-3 relative pt-2">
                    <div className="flex items-start justify-between gap-2 pr-12">
                      <div className="flex items-start gap-3 flex-1">
                        {!doc.metadata?.imageUrl && (
                          <motion.div
                            whileHover={{ rotate: [0, -10, 10, -10, 0], scale: 1.1 }}
                            transition={{ duration: 0.5 }}
                            className="p-3 bg-gradient-to-br from-blue-600 to-blue-500 rounded-xl shadow-lg"
                          >
                            <DocIcon className="h-5 w-5 text-white" />
                          </motion.div>
                        )}
                        <div className="flex-1 min-w-0">
                          <CardTitle className="text-base font-bold line-clamp-2 bg-gradient-to-r from-blue-600 to-blue-500 bg-clip-text text-transparent">
                            {doc.title}
                          </CardTitle>
                          <div className="flex items-center gap-1.5 mt-1.5">
                            <Badge variant="outline" className="text-[10px] bg-blue-100 border-blue-300 text-blue-700">
                              {doc.type}
                            </Badge>
                            {isNewDoc && (
                              <Badge className="text-[10px] bg-yellow-400 text-yellow-900 border-yellow-500">NEW</Badge>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <p
                      className="text-sm text-muted-foreground leading-relaxed"
                      style={{
                        fontFamily:
                          '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                      }}
                    >
                      {doc.content}
                    </p>
                    {(doc.similarity || doc.score) && (
                      <div className="mt-3">
                        <div className="flex items-center gap-2">
                          <Star className="h-3 w-3 text-yellow-500 fill-yellow-500" />
                          <Badge
                            variant="outline"
                            className="text-[10px] bg-gradient-to-r from-yellow-100 to-orange-100 border-yellow-300 text-yellow-800 font-semibold"
                          >
                            {((doc.similarity || doc.score) * 100).toFixed(1)}% Match
                          </Badge>
                        </div>
                      </div>
                    )}
                    {doc.metadata && (
                      <div className="mt-3 pt-3 border-t border-blue-200">
                        <div className="flex flex-wrap gap-1.5">
                          {Object.entries(doc.metadata)
                            .filter(
                              ([key]) =>
                                !key.startsWith("_") &&
                                !key.includes("indexedCreatedAt") &&
                                key !== "imageUrl" &&
                                key !== "vectorSpace",
                            )
                            .slice(0, 4)
                            .map(([key, value], badgeIdx) => {
                              const colors = [
                                "bg-blue-100 text-blue-700 border-blue-300",
                                "bg-green-100 text-green-700 border-green-300",
                                "bg-pink-100 text-pink-700 border-pink-300",
                                "bg-indigo-100 text-indigo-700 border-indigo-300",
                              ];
                              return (
                                <Badge
                                  key={key}
                                  variant="outline"
                                  className={`text-[10px] ${colors[badgeIdx % colors.length]} font-medium`}
                                >
                                  {formatFieldName(key)}: {String(value).slice(0, 25)}
                                </Badge>
                              );
                            })}
                        </div>
                      </div>
                    )}
                  </CardContent>
                </Card>
              </motion.div>
            );
          })}
        </AnimatePresence>
        <div ref={contextPanelEndRef} />
      </div>

      <Button
        variant="ghost"
        size="icon"
        onClick={handleScrollDown}
        className="hidden lg:flex absolute bottom-4 left-1/2 -translate-x-1/2 z-20 h-10 w-10 rounded-full bg-gradient-to-br from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white shadow-xl border-2 border-white/30 hover:scale-110 transition-all"
        title="Scroll Down"
      >
        <ChevronDown className="h-5 w-5" />
      </Button>
    </div>
  );
}

