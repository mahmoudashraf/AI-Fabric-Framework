import { Box, Button, Divider, Paper, Stack, TextField, Typography } from '@mui/material'
import SendOutlinedIcon from '@mui/icons-material/SendOutlined'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { addEscalationReply } from '../api/escalations'
import type { PartnerThread } from '../api/schemas'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { formatDateTime, titleize } from '../utils/format'
import { EvidenceAttachment } from './EvidenceAttachment'

export function EscalationThread({ thread }: { thread: PartnerThread }) {
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const [evidenceIds, setEvidenceIds] = useState('')
  const mutation = useMutation({
    mutationFn: () => addEscalationReply(api, thread.escalation.id, body, evidenceIds.split(',').map((item) => item.trim()).filter(Boolean)),
    onSuccess: async () => {
      setBody('')
      setEvidenceIds('')
      await queryClient.invalidateQueries({ queryKey: ['escalation-thread', thread.escalation.id] })
      await queryClient.invalidateQueries({ queryKey: ['escalations'] })
    },
  })

  return (
    <Stack spacing={2}>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h3">{thread.escalation.title}</Typography>
        <Typography color="text.secondary" sx={{ mt: 0.75 }}>
          {thread.escalation.description}
        </Typography>
      </Paper>
      <Paper sx={{ p: 0 }}>
        <Stack divider={<Divider />}>
          {thread.replies.length === 0 ? (
            <Box sx={{ p: 2 }}>
              <Typography color="text.secondary">No partner-visible replies yet.</Typography>
            </Box>
          ) : (
            thread.replies.map((reply) => (
              <Box key={reply.id} sx={{ p: 2 }}>
                <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
                  <Typography fontWeight={700}>{reply.authorName}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {titleize(reply.authorRole)} · {formatDateTime(reply.createdAt)}
                  </Typography>
                </Stack>
                <Typography sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>{reply.bodyMarkdown}</Typography>
                <Box sx={{ mt: 1.5 }}>
                  <EvidenceAttachment items={reply.attachments} />
                </Box>
              </Box>
            ))
          )}
        </Stack>
      </Paper>
      <Paper sx={{ p: 2 }}>
        <Stack spacing={1.5}>
          <TextField
            label="Reply"
            value={body}
            minRows={4}
            multiline
            onChange={(event) => setBody(event.target.value)}
            placeholder="Add a partner-visible update, reproduction detail, or evidence note."
          />
          <TextField
            label="Evidence bundle IDs"
            value={evidenceIds}
            onChange={(event) => setEvidenceIds(event.target.value)}
            placeholder="peb-... , peb-..."
          />
          <Stack direction="row" justifyContent="flex-end">
            <Button
              variant="contained"
              startIcon={<SendOutlinedIcon />}
              disabled={body.trim().length === 0 || mutation.isPending}
              onClick={() => mutation.mutate()}
            >
              Send reply
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Stack>
  )
}
