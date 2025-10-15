'use client';

// material-ui
import LinkIcon from '@mui/icons-material/Link';
import { Grid, Stack, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import ReactDraftWysiwyg from 'components/forms/plugins/Wysiwug/ReactDraftWysiwyg';
import ReactQuillDemo from 'components/forms/plugins/Wysiwug/ReactQuill';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';

// assets

// ==============================|| PLUGIN - EDITORS ||============================== //

const WysiwygEditor = () => {
  const theme = useTheme();

  return (
    <MainCard
      title="Wysiwyg Editor"
      secondary={
        <SecondaryAction
          icon={<LinkIcon fontSize="small" />}
          link="https://www.npmjs.com/package/react-draft-wysiwyg"
        />
      }
    >
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12 }}
          sx={{
            '& .rdw-editor-wrapper': {
              bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : theme.palette.background.paper,
              border: '1px solid',
              borderColor:
                theme.palette.mode === 'dark' ? theme.palette.dark.light + 20 : 'primary.light',
              borderRadius: '12px',
              overflow: 'scroll',
              '& .rdw-editor-main': {
                px: 2,
                py: 0.5,
                border: 'none',
              },
              '& .rdw-editor-toolbar': {
                pt: 1.25,
                border: 'none',
                borderBottom: '1px solid',
                borderColor:
                  theme.palette.mode === 'dark' ? theme.palette.dark.light + 20 : 'primary.light',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.light' : 'grey.50',
                '& .rdw-option-wrapper': {
                  bgcolor: theme.palette.mode === 'dark' ? 'dark.light' : 'grey.50',
                  borderColor: theme.palette.mode === 'dark' ? theme.palette.dark.dark : 'grey.900',
                },
                '& .rdw-dropdown-wrapper': {
                  bgcolor: theme.palette.mode === 'dark' ? 'dark.light' : 'grey.50',
                  borderColor: theme.palette.mode === 'dark' ? theme.palette.dark.dark : 'grey.900',
                  '& .rdw-dropdown-selectedtext': {
                    color: theme.palette.mode === 'dark' ? 'dark.dark' : 'grey.900',
                  },
                },
              },
            },
          }}
        >
          <Stack spacing={gridSpacing}>
            <Typography variant="subtitle1">React Draft</Typography>
            <ReactDraftWysiwyg />
          </Stack>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Stack
            spacing={gridSpacing}
            sx={{
              '& .quill': {
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
                borderRadius: '12px',
                '& .ql-toolbar': {
                  bgcolor: theme.palette.mode === 'dark' ? 'dark.light' : 'grey.100',
                  borderColor:
                    theme.palette.mode === 'dark' ? theme.palette.dark.light + 20 : 'primary.light',
                  borderTopLeftRadius: '12px',
                  borderTopRightRadius: '12px',
                },
                '& .ql-container': {
                  borderColor:
                    theme.palette.mode === 'dark'
                      ? `${theme.palette.dark.light + 20} !important`
                      : 'primary.light',
                  borderBottomLeftRadius: '12px',
                  borderBottomRightRadius: '12px',
                  '& .ql-editor': {
                    minHeight: 135,
                  },
                },
              },
            }}
          >
            <Typography variant="subtitle1">React Quill</Typography>
            <ReactQuillDemo />
          </Stack>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default WysiwygEditor;
