// material-ui
import { FormControlLabel, Grid, Radio, RadioGroup, Stack, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// third-party
import { Inter, Poppins, Roboto } from 'next/font/google';
import { ChangeEvent } from 'react';

// project imports
import useConfig from 'hooks/useConfig';
import MainCard from 'ui-component/cards/MainCard';

const inter = Inter({ subsets: ['latin'], weight: ['400', '500', '600', '700'] });
const roboto = Roboto({ subsets: ['latin'], weight: ['300', '400', '500', '700'] });
const poppins = Poppins({ subsets: ['latin'], weight: ['400', '500', '600', '700'] });

// ==============================|| CUSTOMIZATION - FONT ||============================== //

const FontFamily = () => {
  const theme = useTheme();

  const { fontFamily, onChangeFontFamily } = useConfig();

  const handleFontChange = (event: ChangeEvent<HTMLInputElement>) => {
    onChangeFontFamily(event.target.value as string);
  };

  const fonts = [
    {
      id: 'inter',
      value: inter.style.fontFamily,
      label: 'Inter',
    },
    {
      id: 'poppins',
      value: poppins.style.fontFamily,
      label: 'Poppins',
    },
    {
      id: 'roboto',
      value: roboto.style.fontFamily,
      label: 'Roboto',
    },
  ];

  const bgColor = theme.palette.mode === 'dark' ? 'dark.800' : 'grey.50';
  const bgActiveColor = theme.palette.mode === 'dark' ? 'primary.800' : 'primary.light';

  return (
    <Stack p={2} spacing={2.5} sx={{ width: '100%' }}>
      <Typography variant="h5">FONT STYLE</Typography>
      <RadioGroup
        aria-label="payment-card"
        name="payment-card"
        value={fontFamily}
        onChange={handleFontChange}
      >
        <Grid container spacing={1.25}>
          {fonts.map((item, index) => (
            <Grid key={index} size={{ xs: 12 }}>
              <MainCard
                content={false}
                sx={{ p: 0.75, bgcolor: fontFamily === item.value ? bgActiveColor : bgColor }}
              >
                <MainCard
                  content={false}
                  border
                  sx={{
                    p: 1.75,
                    borderWidth: 1,
                    ...(fontFamily === item.value && { borderColor: theme.palette.primary.main }),
                  }}
                >
                  <FormControlLabel
                    sx={{ width: 1 }}
                    control={<Radio value={item.value} sx={{ display: 'none' }} />}
                    label={
                      <Typography variant="h5" sx={{ pl: 2, fontFamily: item.value }}>
                        {item.label}
                      </Typography>
                    }
                  />
                </MainCard>
              </MainCard>
            </Grid>
          ))}
        </Grid>
      </RadioGroup>
    </Stack>
  );
};

export default FontFamily;
