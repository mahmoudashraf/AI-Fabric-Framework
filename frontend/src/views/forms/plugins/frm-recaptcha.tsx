'use client';

// material ui
import LinkIcon from '@mui/icons-material/Link';
import { Grid  } from '@mui/material';

// third-party
import ReCAPTCHA from 'react-google-recaptcha';

// project imports
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// assets

// ==============================|| PLUGIN - GOOGLE RECAPTCHA ||============================== //

const RecaptchaPage = () => {
  const handleOnChange = () => {};
  return (
    <MainCard
      title="ReCaptcha"
      secondary={
        <SecondaryAction
          icon={<LinkIcon fontSize="small" />}
          link="https://www.npmjs.com/package/react-google-recaptcha"
        />
      }
    >
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12, md: 12 }}>
          <SubCard sx={{ overflowX: 'auto' }} title="ReCaptcha Example">
            <ReCAPTCHA
              sitekey="6LdzqbcaAAAAALrGEZWQHIHUhzJZc8O-KSTdTTh_"
              onChange={handleOnChange}
            />
          </SubCard>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default RecaptchaPage;
