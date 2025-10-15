// material-ui
import DownloadForOfflineTwoToneIcon from '@mui/icons-material/DownloadForOfflineTwoTone';
import { Card, CardContent, CardMedia, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project import
import { gridSpacing } from 'constants/index';

// assets

const backImage = '/assets/images/profile';

// ==============================|| ATTACHMENT CARD ||============================== //

interface AttachmentCardProps {
  title: string;
  image: string;
}

const AttachmentCard = ({ title, image }: AttachmentCardProps) => {
  const theme = useTheme();
  const backProfile = image && `${backImage}/${image}`;

  return (
    <Card sx={{ bgcolor: theme.palette.mode === 'dark' ? 'dark.dark' : 'grey.100' }}>
      <CardMedia component="img" image={backProfile} title="Slider5 image" />
      <CardContent sx={{ p: 2, pb: '16px !important' }}>
        <Grid container spacing={gridSpacing}>
          <Grid size="grow" sx={{ minWidth: 0 }}>
            <Typography
              variant="h5"
              component="div"
              sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
            >
              {title}
            </Typography>
          </Grid>
          <Grid>
            <DownloadForOfflineTwoToneIcon sx={{ cursor: 'pointer' }} />
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );
};

export default AttachmentCard;
