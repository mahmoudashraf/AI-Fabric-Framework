// material-ui

// assets
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import SentimentDissatisfiedIcon from '@mui/icons-material/SentimentDissatisfied';
import SentimentSatisfiedIcon from '@mui/icons-material/SentimentSatisfied';
import SentimentSatisfiedAltIcon from '@mui/icons-material/SentimentSatisfiedAltOutlined';
import SentimentVeryDissatisfiedIcon from '@mui/icons-material/SentimentVeryDissatisfied';
import SentimentVerySatisfiedIcon from '@mui/icons-material/SentimentVerySatisfied';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import { Grid, Rating, Typography  } from '@mui/material';
import { styled } from '@mui/material/styles';

// types
import { KeyedObject } from 'types';

// style constant
const StyledRating = styled(Rating)({
  '& .MuiRating-iconFilled': {
    color: '#ff6d75',
  },
  '& .MuiRating-iconHover': {
    color: '#ff3d47',
  },
});

// customized icons
const customIcons: KeyedObject = {
  1: {
    icon: <SentimentVeryDissatisfiedIcon />,
    label: 'Very Dissatisfied',
  },
  2: {
    icon: <SentimentDissatisfiedIcon />,
    label: 'Dissatisfied',
  },
  3: {
    icon: <SentimentSatisfiedIcon />,
    label: 'Neutral',
  },
  4: {
    icon: <SentimentSatisfiedAltIcon />,
    label: 'Satisfied',
  },
  5: {
    icon: <SentimentVerySatisfiedIcon />,
    label: 'Very Satisfied',
  },
};

// ===============================|| CUSTOMIZED ICON ||=============================== //

function IconContainer({ value, ...other }: { value: number }) {
  return <span {...other}>{customIcons[value].icon}</span>;
}

// ===============================|| UI RATING - CUSTOMIZED ||=============================== //

export default function CustomizedRatings() {
  return (
    <>
      <Grid container spacing={2} justifyContent={{ lg: 'center' }}>
        <Grid>
          <Typography component="legend">Empty Icon</Typography>
        </Grid>
        <Grid>
          <Rating
            name="customized-empty"
            defaultValue={2}
            precision={0.5}
            emptyIcon={<StarBorderIcon fontSize="inherit" />}
          />
        </Grid>
      </Grid>
      <Grid container spacing={2} justifyContent={{ lg: 'center' }}>
        <Grid>
          <Typography component="legend">Icon & Color</Typography>
        </Grid>
        <Grid>
          <StyledRating
            name="customized-color"
            defaultValue={2}
            getLabelText={value => `${value} Heart${value !== 1 ? 's' : ''}`}
            precision={0.5}
            icon={<FavoriteIcon fontSize="inherit" />}
            emptyIcon={<FavoriteBorderIcon sx={{ color: '#ff6d75' }} fontSize="inherit" />}
          />
        </Grid>
      </Grid>
      <Grid container spacing={2} justifyContent={{ lg: 'center' }}>
        <Grid>
          <Typography component="legend">6 Stars</Typography>
        </Grid>
        <Grid>
          <Rating name="customized-10" defaultValue={2} max={8} />
        </Grid>
      </Grid>
      <Grid container spacing={2} justifyContent={{ lg: 'center' }}>
        <Grid>
          <Typography component="legend">Icon Set</Typography>
        </Grid>
        <Grid>
          <Rating
            name="customized-icons"
            defaultValue={2}
            getLabelText={value => customIcons[value].label}
            IconContainerComponent={IconContainer}
          />
        </Grid>
      </Grid>
    </>
  );
}
