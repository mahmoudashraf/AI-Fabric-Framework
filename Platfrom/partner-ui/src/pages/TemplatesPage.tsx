import { Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'

const templates = [
  { title: 'Fashion/apparel', body: 'Sizing, reviews, fit guidance, product context, and policy clarity.' },
  { title: 'Electronics', body: 'Comparison-heavy buying flows, specifications, compatibility, and setup guidance.' },
  { title: 'Health/beauty', body: 'Ingredient, use-case, routine, and policy questions with merchant-safe claims.' },
  { title: 'Home/furniture', body: 'Dimensions, materials, delivery, returns, and room-fit context.' },
]

export function TemplatesPage() {
  const navigate = useNavigate()
  return (
    <>
      <PageHeader title="Templates & playbooks" subtitle="Starter-safe implementation playbooks for common store categories." />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 2 }}>
        {templates.map((template) => (
          <Paper key={template.title} sx={{ p: 2 }}>
            <Stack spacing={1.5}>
              <Typography variant="h3">{template.title}</Typography>
              <Typography color="text.secondary">{template.body}</Typography>
              <Button sx={{ alignSelf: 'flex-start' }} onClick={() => navigate('/catalog')}>Map to catalog</Button>
            </Stack>
          </Paper>
        ))}
      </Box>
    </>
  )
}
