import { Button } from '@mui/material'
import NorthEastRounded from '@mui/icons-material/NorthEastRounded'
import { Link } from 'react-router-dom'

export function ConsoleEntryButton({ to }: { to: string }) {
  return <Button component={Link} to={to} variant="contained" startIcon={<NorthEastRounded />}
    sx={theme => ({
      '&&&': {
        background: theme.palette.mode === 'dark'
          ? 'linear-gradient(135deg, #087dba, #2458e0 44%, #7441e9)'
          : 'linear-gradient(135deg, #04c7ee, #146cff 44%, #7b3ff2)',
        border: '1px solid rgb(20 108 255 / 25%)',
        borderRadius: '8px',
        boxShadow: '0 8px 20px rgb(67 74 231 / 17%)',
        color: '#fff',
        fontFamily: 'inherit',
        fontSize: '16px',
        fontWeight: 600,
        letterSpacing: 0,
        lineHeight: 1.4,
        minHeight: '46px',
        padding: '10px 20px',
        textDecoration: 'none',
        textTransform: 'none',
        whiteSpace: 'nowrap',
        '&:hover': { filter: 'brightness(1.08)', boxShadow: '0 10px 24px rgb(67 74 231 / 24%)', textDecoration: 'none' },
        '&.Mui-focusVisible': { outline: `2px solid ${theme.palette.primary.main}`, outlineOffset: '3px' },
        '& .MuiButton-startIcon': { marginLeft: 0, marginRight: '8px', '& svg': { fontSize: '18px' } },
      },
    })}>Open Console</Button>
}
