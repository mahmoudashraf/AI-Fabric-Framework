import { createTheme } from '@mui/material/styles'

export const platformTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#3b82c4',
      dark: '#244f80',
      light: '#7fb4df',
      contrastText: '#f7fbff',
    },
    secondary: {
      main: '#d46c4a',
    },
    background: {
      default: '#eef3f8',
      paper: '#fbfcfe',
    },
    text: {
      primary: '#0f1724',
      secondary: '#526070',
    },
  },
  shape: {
    borderRadius: 16,
  },
  typography: {
    fontFamily: '"Plus Jakarta Sans", "Segoe UI", sans-serif',
    h4: {
      fontWeight: 800,
    },
    h6: {
      fontWeight: 700,
    },
    button: {
      textTransform: 'none',
      fontWeight: 700,
    },
  },
  components: {
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundImage:
            'linear-gradient(180deg, rgba(255,255,255,0.98), rgba(246,249,252,0.98))',
        },
      },
    },
  },
})
