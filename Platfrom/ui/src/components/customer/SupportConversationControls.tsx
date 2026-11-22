import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, List, ListItemButton, ListItemText, Stack, Tooltip, Typography } from '@mui/material'
import HistoryRounded from '@mui/icons-material/HistoryRounded'
import AddCommentOutlined from '@mui/icons-material/AddCommentOutlined'
import TuneRounded from '@mui/icons-material/TuneRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import { getSupportHistory, supportErrorMessage, type SupportHistory } from '../../api/supportApi'
import { useCustomerSupport } from './CustomerSupportContext'

export function SupportConversationControls({ direction = 'row' }: { direction?: 'row' | 'column' }) {
  const support = useCustomerSupport()
  const [history, setHistory] = useState(false)
  const [confirm, setConfirm] = useState(false)
  return <>
    <Stack direction={direction} spacing={0.25}>
      <Tooltip title="Configure diagnostics"><IconButton size="small" aria-label="Configure diagnostics" disabled={support.busy || !support.conversationId} onClick={() => support.setDiagnosticsOpen(true)}><TuneRounded fontSize="small" /></IconButton></Tooltip>
      <Tooltip title="Support chat history"><IconButton size="small" aria-label="Support chat history" disabled={support.busy} onClick={() => setHistory(true)}><HistoryRounded fontSize="small" /></IconButton></Tooltip>
      <Tooltip title="New support chat"><IconButton size="small" aria-label="New support chat" disabled={support.busy} onClick={() => setConfirm(true)}><AddCommentOutlined fontSize="small" /></IconButton></Tooltip>
    </Stack>
    <Dialog open={history} onClose={() => setHistory(false)} fullWidth maxWidth="sm">
      <DialogTitle><Stack direction="row" alignItems="center" justifyContent="space-between">Support chat history<IconButton aria-label="Close chat history" onClick={() => setHistory(false)}><CloseRounded /></IconButton></Stack></DialogTitle>
      <DialogContent>{history && <HistoryList onSelect={id => { support.openConversation(id); setHistory(false) }} />}</DialogContent>
    </Dialog>
    <Dialog open={confirm} onClose={() => setConfirm(false)} fullWidth maxWidth="xs">
      <DialogTitle>Start a new support chat?</DialogTitle>
      <DialogContent>Your current chat stays in history. Resource selections and diagnostic permission do not carry over.</DialogContent>
      <DialogActions><Button onClick={() => setConfirm(false)}>Cancel</Button><Button variant="contained" onClick={() => { support.openConversation(); setConfirm(false) }}>New chat</Button></DialogActions>
    </Dialog>
  </>
}

function HistoryList({ onSelect }: { onSelect(id: string): void }) {
  const support = useCustomerSupport()
  const [cursor, setCursor] = useState<string | undefined>()
  const [older, setOlder] = useState<SupportHistory['items']>([])
  const query = useQuery({ queryKey: ['support-history', support.ownerKey, cursor], queryFn: () => getSupportHistory(cursor), retry: false, gcTime: 0 })
  if (query.isError) return <Alert severity="warning" action={<Button onClick={() => void query.refetch()}>Refresh</Button>}>{supportErrorMessage(query.error)}</Alert>
  const items = [...older, ...(query.data?.items ?? [])]
  return <>
    <Typography variant="body2" color="text.secondary">General conversations. Tracked requests remain in My requests.</Typography>
    <List>{items.map(item => <ListItemButton key={item.conversationId} selected={!support.requestId && item.conversationId === support.conversationId} onClick={() => onSelect(item.conversationId)}>
      <ListItemText primary={`Chat · ${new Date(item.createdAt).toLocaleString()}`} secondary={item.conversationId === support.conversationId && !support.requestId ? 'Current conversation' : undefined} />
    </ListItemButton>)}</List>
    {query.isPending && <CircularProgress size={20} aria-label="Loading support chat history" />}
    {query.data && items.length === 0 && <Typography>No general conversations yet.</Typography>}
    {query.data?.nextCursor && <Button onClick={() => { setOlder(items); setCursor(query.data!.nextCursor!) }}>Load older chats</Button>}
  </>
}
