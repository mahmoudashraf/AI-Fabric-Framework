// material-ui
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import { Button,
  ButtonBase,
  CardContent,
  Grid,
  Tooltip,
  Typography,
  useMediaQuery,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
const gridSpacing = 3;
import { ProductsFilter } from 'types/e-commerce';
import SubCard from 'ui-component/cards/SubCard';
import Avatar from 'ui-component/extended/Avatar';
import Chip from 'ui-component/extended/Chip';

import ColorOptions from '../ColorOptions';

// assets

// types

function getColor(color: string) {
  return ColorOptions.filter(item => item.value === color);
}

interface ProductFilterViewProps {
  filter: ProductsFilter;
  initialState: ProductsFilter;
  filterIsEqual: (initialState: ProductsFilter, filter: ProductsFilter) => boolean;
  handelFilter: (type: string, params: string, rating?: number) => void;
}

// ==============================|| PRODUCT GRID - FILTER VIEW ||============================== //

const ProductFilterView = ({
  filter,
  filterIsEqual,
  handelFilter,
  initialState,
}: ProductFilterViewProps) => {
  const theme = useTheme();
  const matchDownMD = useMediaQuery(theme.breakpoints.down('lg'));

  return (
    <>
      {!filterIsEqual(initialState, filter) && (
        <Grid container spacing={gridSpacing} sx={{ pb: gridSpacing }} alignItems="center">
          {!(initialState.search === filter.search) && (
            <Grid>
              <SubCard content={false}>
                <CardContent sx={{ pb: '12px !important', p: 1.5 }}>
                  <Grid container spacing={1} alignItems="center">
                    <Grid>
                      <Chip
                        size={matchDownMD ? 'small' : 'medium'}
                        label={filter.search}
                        chipcolor="secondary"
                        onDelete={() => handelFilter('search', '')}
                        sx={{ borderRadius: '4px', textTransform: 'capitalize' }}
                      />
                    </Grid>
                  </Grid>
                </CardContent>
              </SubCard>
            </Grid>
          )}
          {!(initialState.sort === filter.sort) && (
            <Grid>
              <SubCard content={false}>
                <CardContent sx={{ pb: '12px !important', p: 1.5 }}>
                  <Grid container spacing={1} alignItems="center">
                    <Grid>
                      <Typography variant="subtitle1">Sort</Typography>
                    </Grid>
                    <Grid>
                      <Chip
                        size={matchDownMD ? 'small' : 'medium'}
                        label={filter.sort}
                        chipcolor="secondary"
                        onDelete={() => handelFilter('sort', initialState.sort)}
                        sx={{ borderRadius: '4px', textTransform: 'capitalize' }}
                      />
                    </Grid>
                  </Grid>
                </CardContent>
              </SubCard>
            </Grid>
          )}
          {!(JSON.stringify(initialState.gender) === JSON.stringify(filter.gender)) && (
            <Grid>
              <SubCard content={false}>
                <CardContent sx={{ pb: '12px !important', p: 1.5 }}>
                  <Grid container spacing={1} alignItems="center">
                    <Grid>
                      <Typography variant="subtitle1">Gender</Typography>
                    </Grid>

                    {filter.gender.map((item: string, index: number) => {
                      let color = 'secondary';
                      if (item === 'male') color = 'primary';
                      if (item === 'kids') color = 'error';
                      return (
                        <Grid key={index}>
                          <Chip
                            size={matchDownMD ? 'small' : 'medium'}
                            label={item}
                            onDelete={() => handelFilter('gender', item)}
                            chipcolor={color}
                            sx={{ borderRadius: '4px', textTransform: 'capitalize' }}
                          />
                        </Grid>
                      );
                    })}
                  </Grid>
                </CardContent>
              </SubCard>
            </Grid>
          )}
          {!(JSON.stringify(initialState.categories) === JSON.stringify(filter.categories)) &&
            filter.categories.length > 0 && (
              <Grid>
                <SubCard content={false}>
                  <CardContent sx={{ pb: '12px !important', p: 1.5 }}>
                    <Grid container spacing={1} alignItems="center">
                      <Grid>
                        <Typography variant="subtitle1">Categories</Typography>
                      </Grid>
                      {filter.categories.map((item: string, index: number) => (
                        <Grid key={index}>
                          <Chip
                            size={matchDownMD ? 'small' : 'medium'}
                            label={item}
                            onDelete={() => handelFilter('categories', item)}
                            chipcolor="secondary"
                            sx={{ borderRadius: '4px', textTransform: 'capitalize' }}
                          />
                        </Grid>
                      ))}
                    </Grid>
                  </CardContent>
                </SubCard>
              </Grid>
            )}
          {!(JSON.stringify(initialState.colors) === JSON.stringify(filter.colors)) && (
            <Grid>
              <SubCard content={false}>
                <CardContent sx={{ pb: '12px !important', p: 1.5 }}>
                  <Grid container spacing={1} alignItems="center">
                    <Grid>
                      <Typography variant="subtitle1">Colors</Typography>
                    </Grid>
                    {filter.colors.map((item: string, index: number) => {
                      const colorsData = getColor(item);
                      return (
                        <Grid key={index}>
                          <Tooltip title={colorsData[0]?.label || ''}>
                            <ButtonBase
                              sx={{ borderRadius: '50%' }}
                              onClick={() => handelFilter('colors', item)}
                            >
                              <Avatar
                                color="inherit"
                                size="badge"
                                sx={{
                                  bgcolor: colorsData[0]?.bg || '#000',
                                  color: theme.palette.mode === 'light' ? 'grey.50' : 'grey.800',
                                }}
                              >
                                <CheckIcon
                                  sx={{
                                    color: theme.palette.mode === 'light' ? 'grey.50' : 'grey.800',
                                  }}
                                  fontSize="inherit"
                                />
                              </Avatar>
                            </ButtonBase>
                          </Tooltip>
                        </Grid>
                      );
                    })}
                  </Grid>
                </CardContent>
              </SubCard>
            </Grid>
          )}
          {!(initialState.price === filter.price) && (
            <Grid>
              <SubCard content={false}>
                <CardContent sx={{ pb: '12px !important', p: 1.5 }}>
                  <Grid container spacing={1} alignItems="center">
                    <Grid>
                      <Typography variant="subtitle1">Price</Typography>
                    </Grid>
                    <Grid>
                      <Chip
                        size={matchDownMD ? 'small' : 'medium'}
                        label={filter.price}
                        chipcolor="primary"
                        sx={{ borderRadius: '4px', textTransform: 'capitalize' }}
                      />
                    </Grid>
                  </Grid>
                </CardContent>
              </SubCard>
            </Grid>
          )}
          {!(initialState.rating === filter.rating) && (
            <Grid>
              <SubCard content={false}>
                <CardContent sx={{ pb: '12px !important', p: 1.5 }}>
                  <Grid container spacing={1} alignItems="center">
                    <Grid>
                      <Typography variant="subtitle1">Rating</Typography>
                    </Grid>
                    <Grid>
                      <Chip
                        size={matchDownMD ? 'small' : 'medium'}
                        label={String(filter.rating)}
                        chipcolor="warning"
                        onDelete={() => handelFilter('rating', '', 0)}
                        sx={{ borderRadius: '4px', textTransform: 'capitalize' }}
                      />
                    </Grid>
                  </Grid>
                </CardContent>
              </SubCard>
            </Grid>
          )}
          <Grid>
            <Button
              variant="outlined"
              startIcon={<CloseIcon />}
              color="error"
              onClick={() => handelFilter('reset', '')}
            >
              Clear All
            </Button>
          </Grid>
        </Grid>
      )}
    </>
  );
};

export default ProductFilterView;
