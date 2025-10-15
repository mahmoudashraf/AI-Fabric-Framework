'use client';

import { useRef, useState, useCallback, memo } from 'react';

// third-party
import Map, { MapRef } from 'react-map-gl';

// project-import
import { MapBoxProps } from 'types/map';
import MapControl from 'ui-component/third-party/map/MapControl';

import ControlPanel, { CityProps } from './control-panel';

// types

// ==============================|| MAP - VIEWPORT ANIMATION ||============================== //

interface Props extends MapBoxProps {
  data: CityProps[];
}

function ViewportAnimation({ data, ...other }: Props) {
  const mapRef = useRef<MapRef>(null);

  const [selectedCity, setSelectedCity] = useState(data[2]?.city ?? '');

  const onSelectCity = useCallback(
    (
      event: React.ChangeEvent<HTMLInputElement>,
      { longitude, latitude }: { longitude: number; latitude: number }
    ) => {
      setSelectedCity(event.target.value);
      mapRef.current?.flyTo({ center: [longitude, latitude], duration: 2000 });
    },
    []
  );

  return (
    <Map
      {...(other as any)}
      initialViewState={{
        latitude: 22.299405,
        longitude: 73.208119,
        zoom: 11,
        bearing: 0,
        pitch: 0,
      }}
      ref={mapRef}
      minZoom={0}
      maxZoom={22}
      minPitch={0}
      maxPitch={85}
      interactive={true}
    >
      <MapControl />
      <ControlPanel data={data} selectedCity={selectedCity} onSelectCity={onSelectCity} />
    </Map>
  );
}

export default memo(ViewportAnimation);
