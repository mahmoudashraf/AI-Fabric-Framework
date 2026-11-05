import { Button, type ButtonProps } from '@mui/material'
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined'
import { integrationTeam } from '../integrationTeam'

type Props = Pick<ButtonProps, 'variant' | 'size' | 'fullWidth' | 'className' | 'sx'> & { children?: string }

export function IntegrationCallButton({ children = 'Book integration call', ...props }: Props) {
  return <Button component="a" href={integrationTeam.bookingUrl} target="_blank" rel="noopener noreferrer"
    startIcon={<CalendarMonthOutlined />} {...props}>{children}</Button>
}
