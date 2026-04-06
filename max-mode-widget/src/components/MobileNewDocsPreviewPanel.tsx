import { AnimatePresence, motion } from "framer-motion";
import { FileText, Image as ImageIcon, Package, Sparkles, Star, X } from "lucide-react";

import { Button } from "@/ui/button";
import { Card, CardHeader, CardTitle } from "@/ui/card";

import type { Document } from "@/types";

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

export function MobileNewDocsPreviewPanel({
  isOpen,
  newDocuments,
  onClose,
  onSelectDocument,
  onViewAll,
}: {
  isOpen: boolean;
  newDocuments: Document[];
  onClose: () => void;
  onSelectDocument: (doc: Document) => void;
  onViewAll: () => void;
}) {
  return (
    <AnimatePresence>
      {isOpen && newDocuments.length > 0 && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="md:hidden fixed inset-0 bg-black/30 z-[35]"
            onClick={onClose}
          />

          <motion.div
            initial={{ x: 300, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: 300, opacity: 0 }}
            transition={{ type: "spring", damping: 25, stiffness: 200 }}
            className="md:hidden fixed top-20 bottom-20 right-0 w-[280px] bg-gradient-to-b from-blue-50 to-white dark:from-gray-900 dark:via-blue-900/20 dark:to-gray-900 shadow-2xl z-[40] flex flex-col rounded-l-3xl border-l-2 border-blue-300"
          >
            <div className="px-4 py-3 border-b border-blue-200 dark:border-blue-800 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="p-2 bg-gradient-to-br from-blue-600 to-blue-500 rounded-lg">
                  <Sparkles className="h-4 w-4 text-white" />
                </div>
                <div>
                  <h3 className="text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 bg-clip-text text-transparent">
                    New Products
                  </h3>
                  <p className="text-[10px] text-muted-foreground">
                    {newDocuments.length} {newDocuments.length === 1 ? "item" : "items"}
                  </p>
                </div>
              </div>
              <Button variant="ghost" size="icon" onClick={onClose} className="h-8 w-8">
                <X className="h-4 w-4" />
              </Button>
            </div>

            <div className="flex-1 overflow-y-auto px-3 py-3 space-y-2">
              {newDocuments.map((doc, idx) => {
                const DocIcon = getDocumentIcon(doc.type);
                return (
                  <motion.div
                    key={doc.id}
                    initial={{ opacity: 0, x: 20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: idx * 0.05 }}
                  >
                    <Card
                      onClick={() => onSelectDocument(doc)}
                      className="relative group active:scale-98 transition-all border-2 border-yellow-300 bg-gradient-to-br from-yellow-50/80 via-blue-50/30 to-white/30 cursor-pointer shadow-lg"
                    >
                      {doc.metadata?.imageUrl && (
                        <div className="relative h-24 overflow-hidden bg-gradient-to-br from-blue-100 to-white rounded-t-lg">
                          <img src={doc.metadata.imageUrl} alt={doc.title} className="w-full h-full object-cover" />
                          <div className="absolute top-1 right-1 bg-yellow-400 text-yellow-900 text-[8px] font-bold px-1.5 py-0.5 rounded-full">
                            NEW
                          </div>
                        </div>
                      )}
                      <CardHeader className="pb-2 pt-2 px-2">
                        <div className="flex items-start gap-2">
                          {!doc.metadata?.imageUrl && (
                            <>
                              <div className="p-1.5 bg-gradient-to-br from-blue-600 to-blue-500 rounded-lg flex-shrink-0">
                                <DocIcon className="h-3 w-3 text-white" />
                              </div>
                              <div className="absolute top-1 right-1 bg-yellow-400 text-yellow-900 text-[8px] font-bold px-1.5 py-0.5 rounded-full">
                                NEW
                              </div>
                            </>
                          )}
                          <div className="flex-1 min-w-0">
                            <CardTitle className="text-xs font-bold line-clamp-2 text-gray-900">{doc.title}</CardTitle>
                            {doc.metadata?.price && (
                              <p className="text-xs font-semibold text-gray-900 mt-1">{doc.metadata.price}</p>
                            )}
                          </div>
                        </div>
                      </CardHeader>
                    </Card>
                  </motion.div>
                );
              })}
            </div>

            <div className="p-3 border-t border-blue-200">
              <Button
                onClick={onViewAll}
                className="w-full bg-gradient-to-r from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white shadow-lg"
                size="sm"
              >
                View All Documents
              </Button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}

