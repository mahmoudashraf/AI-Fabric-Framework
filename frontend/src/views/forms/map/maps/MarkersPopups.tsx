'use client';

import { Box, Typography } from '@mui/material';
import { useState, memo } from 'react';

// material-ui

// third-party
import Map from 'react-map-gl';

// project-import
import { MapBoxProps } from 'types/map';
import MapControl from 'ui-component/third-party/map/MapControl';
import MapMarker from 'ui-component/third-party/map/MapMarker';
import MapPopup from 'ui-component/third-party/map/MapPopup';

// types

type CountryProps = {
  name: string;
  capital: string;
  latlng: number[];
  timezones: string[];
  country_code: string;
};

interface Props extends MapBoxProps {
  data: CountryProps[];
}

// ==============================|| MAPBOX - MARKERS AND POPUP ||============================== //

const MarkersPopups = ({ data, ...other }: Props) => {
  const [popupInfo, setPopupInfo] = useState<CountryProps | null>(null);

  return (
    <Map
      {...(other as any)}
      initialViewState={{
        latitude: 21.2335611,
        longitude: 72.8636084,
        zoom: 2,
      }}
      zoom={2}
      minZoom={0}
      maxZoom={22}
      minPitch={0}
      maxPitch={85}
      interactive={true}
    >
      <MapControl />
      {data.map((city, index) => (
        <MapMarker
          key={`marker-${index}`}
          latitude={Number(city.latlng?.[0] ?? 0)}
          longitude={Number(city.latlng?.[1] ?? 0)}
          onClick={(event: any) => {
            event.originalEvent.stopPropagation();
            setPopupInfo(city);
          }}
        />
      ))}

      {popupInfo && (
        <MapPopup
          latitude={Number(popupInfo.latlng?.[0] ?? 0)}
          longitude={Number(popupInfo.latlng?.[1] ?? 0)}
          onClose={() => setPopupInfo(null)}
        >
          <Box
            sx={{
              mb: 1,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <Box
              sx={{
                height: 18,
                minWidth: 28,
                mr: 1,
                borderRadius: 2,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
                backgroundRepeat: 'no-repeat',
                backgroundImage: `url(https://cdn.staticaly.com/gh/hjnilsson/country-flags/master/svg/${popupInfo.country_code.toLowerCase()}.svg)`,
              }}
            />
            <Typography variant="subtitle2">{popupInfo.name}</Typography>
          </Box>

          <Typography component="div" variant="caption">
            Timezones: {popupInfo.timezones}
          </Typography>

          <Typography component="div" variant="caption">
            Lat: {popupInfo.latlng[0]}
          </Typography>

          <Typography component="div" variant="caption">
            Long: {popupInfo.latlng[1]}
          </Typography>
        </MapPopup>
      )}
    </Map>
  );
};

export default memo(MarkersPopups);
