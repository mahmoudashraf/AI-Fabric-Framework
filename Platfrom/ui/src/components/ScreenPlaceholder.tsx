import {
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Typography,
} from '@mui/material'

type ScreenPlaceholderProps = {
  eyebrow: string
  title: string
  description: string
  bullets: string[]
}

export function ScreenPlaceholder({
  eyebrow,
  title,
  description,
  bullets,
}: ScreenPlaceholderProps) {
  return (
    <Stack spacing={3}>
      <Box>
        <Chip label={eyebrow} color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          {title}
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 880 }}>
          {description}
        </Typography>
      </Box>

      <Grid container spacing={2}>
        {bullets.map((item) => (
          <Grid item xs={12} md={6} key={item}>
            <Card
              sx={{
                height: '100%',
                borderRadius: 3,
                border: '1px solid',
                borderColor: 'divider',
                boxShadow: 'none',
              }}
            >
              <CardContent>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {item}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Stack>
  )
}

