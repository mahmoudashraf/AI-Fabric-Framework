import { AnimatePresence, motion } from "framer-motion";
import { BrainCircuit, CheckCircle2, FileText, Image as ImageIcon, Package, Star } from "lucide-react";

import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";

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

export function DocumentsView({
  contextDocuments,
  newDocuments,
  viewedDocumentIds,
  isItemAttached,
  onOpenProductDetails,
  onAttachDocument,
}: {
  contextDocuments: Document[];
  newDocuments: Document[];
  viewedDocumentIds: Set<string>;
  isItemAttached: (itemId: string) => boolean;
  onOpenProductDetails: (doc: Document) => void;
  onAttachDocument: (doc: Document) => void;
}) {
  return (
    <AnimatePresence mode="popLayout">
      {[...contextDocuments].reverse().map((doc, idx) => {
        const DocIcon = getDocumentIcon(doc.type);
        const isNewDoc = !viewedDocumentIds.has(doc.id) && newDocuments.some((nd) => nd.id === doc.id);

        return (
          <motion.div
            key={doc.id}
            data-doc-id={doc.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            transition={{ delay: idx * 0.03 }}
          >
            <Card
              onClick={() => onOpenProductDetails(doc)}
              className={`relative group active:scale-98 transition-all border-2 cursor-pointer ${
                isNewDoc
                  ? "border-yellow-400 bg-gradient-to-br from-yellow-50/90 via-blue-50/40 to-white/40 shadow-lg"
                  : "border-blue-200 hover:border-blue-400 bg-gradient-to-br from-white via-blue-50/30 to-white"
              }`}
            >
              {doc.metadata?.imageUrl && (
                <div className="relative h-32 overflow-hidden bg-gradient-to-br from-blue-100 to-white">
                  <img src={doc.metadata.imageUrl} alt={doc.title} className="w-full h-full object-cover" />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
                  {isNewDoc && (
                    <div className="absolute top-2 right-2 bg-yellow-400 text-yellow-900 text-[9px] font-bold px-2 py-1 rounded-full shadow-lg">
                      NEW
                    </div>
                  )}
                </div>
              )}
              <CardHeader className="pb-2 pt-3 px-3">
                <div className="flex items-start gap-2">
                  {!doc.metadata?.imageUrl && (
                    <div className="p-2 bg-gradient-to-br from-blue-600 to-blue-500 rounded-lg flex-shrink-0">
                      <DocIcon className="h-4 w-4 text-white" />
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <CardTitle className="text-sm font-bold line-clamp-2 bg-gradient-to-r from-blue-600 to-blue-500 bg-clip-text text-transparent">
                      {doc.title}
                    </CardTitle>
                    <div className="flex items-center gap-1 mt-1">
                      <Badge variant="outline" className="text-[9px] bg-blue-100 border-blue-300 text-blue-700">
                        {doc.type}
                      </Badge>
                      {isNewDoc && <Badge className="text-[9px] bg-yellow-400 text-yellow-900 border-yellow-500">NEW</Badge>}
                    </div>
                  </div>
                  <Button
                    size="icon"
                    variant="ghost"
                    className={`h-9 w-9 flex-shrink-0 ${
                      isItemAttached(doc.id)
                        ? "bg-gradient-to-br from-green-500 to-emerald-500"
                        : "bg-gradient-to-br from-blue-600 to-blue-500"
                    } text-white shadow-lg border border-white/30 hover:scale-110 transition-all z-50 pointer-events-auto cursor-pointer`}
                    onClick={(e) => {
                      e.stopPropagation();
                      e.preventDefault();
                      onAttachDocument(doc);
                    }}
                    title={isItemAttached(doc.id) ? "Already in Chat" : "Attach to Chat"}
                    aria-label={isItemAttached(doc.id) ? "Already attached" : "Attach to chat"}
                  >
                    {isItemAttached(doc.id) ? <CheckCircle2 className="h-4 w-4" /> : <BrainCircuit className="h-4 w-4" />}
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="px-3 pb-3">
                <p className="text-xs text-muted-foreground leading-relaxed line-clamp-2">{doc.content}</p>
              </CardContent>
            </Card>
          </motion.div>
        );
      })}
    </AnimatePresence>
  );
}

