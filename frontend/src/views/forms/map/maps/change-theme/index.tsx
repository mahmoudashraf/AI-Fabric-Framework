'use client';

import { useState, useCallback, memo } from 'react';

// third-party
import Map from 'react-map-gl';

// project-imports
import { MapBoxProps } from 'types/map';
import MapControl from 'ui-component/third-party/map/MapControl';

import ControlPanel from './control-panel';

// types

interface Props extends MapBoxProps {
  themes: {
    [key: string]: string;
  };
}

// ==============================|| MAPBOX - THEME ||============================== //

const ChangeTheme = ({ themes, ...other }: Props) => {
  const [selectTheme, setSelectTheme] = useState('streets');
  const handleChangeTheme = useCallback((value: string) => setSelectTheme(value), []);

  return (
    <>
      <Map
        {...(other as any)}
        initialViewState={{
          latitude: 21.2335611,
          longitude: 72.8636084,
          zoom: 6,
          bearing: 0,
          pitch: 0,
        }}
        mapStyle={themes?.[selectTheme]}
        minZoom={0}
        maxZoom={22}
        minPitch={0}
        maxPitch={85}
        interactive={true}
      >
        <MapControl />
      </Map>

      <ControlPanel themes={themes} selectTheme={selectTheme} onChangeTheme={handleChangeTheme} />
    </>
  );
};

export default memo(ChangeTheme);
