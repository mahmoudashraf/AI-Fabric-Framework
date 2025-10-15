'use client';

//material-ui
import { styled } from '@mui/material/styles';

// third-party
import {
  IconCircleCheck,
  IconSquareRoundedX,
  IconInfoCircle,
  IconAlertCircle,
} from '@tabler/icons-react';
import { SnackbarProvider } from 'notistack';

// project import
// Note: Notistack is a provider component itself, doesn't need Context API

// assets

// custom styles
const StyledSnackbarProvider = styled(SnackbarProvider)(({ theme }) => ({
  '&.notistack-MuiContent-default': {
    backgroundColor: theme.palette.primary.main,
  },
  '&.notistack-MuiContent-error': {
    backgroundColor: theme.palette.error.main,
  },
  '&.notistack-MuiContent-success': {
    backgroundColor: theme.palette.success.main,
  },
  '&.notistack-MuiContent-info': {
    backgroundColor: theme.palette.info.main,
  },
  '&.notistack-MuiContent-warning': {
    backgroundColor: theme.palette.warning.main,
  },
}));

// ===========================|| SNACKBAR - NOTISTACK ||=========================== //

const Notistack = ({ children }: any) => {
  // Default snackbar settings - Notistack provides its own configuration
  const snackbar: { maxStack: number; dense: boolean; iconVariant: 'hide' | 'useemojis' } = {
    maxStack: 3,
    dense: false,
    iconVariant: 'hide',
  };
  const iconSX = { marginRight: 8, fontSize: '1.15rem' };

  return (
    <StyledSnackbarProvider
      maxSnack={snackbar.maxStack}
      dense={snackbar.dense}
      iconVariant={
        snackbar.iconVariant === 'useemojis'
          ? {
              success: <IconCircleCheck style={iconSX} />,
              error: <IconSquareRoundedX style={iconSX} />,
              warning: <IconInfoCircle style={iconSX} />,
              info: <IconAlertCircle style={iconSX} />,
            }
          : ({} as any)
      }
      hideIconVariant={snackbar.iconVariant === 'hide'}
    >
      {children}
    </StyledSnackbarProvider>
  );
};

export default Notistack;
