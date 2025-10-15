'use client';
import {
  Card,
  CardContent,
  CardHeader,
  CardActions as MuiCardActions,
  Divider,
  Typography,
  CardProps,
  CardHeaderProps,
  CardContentProps,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React, { Ref, type ForwardRefExoticComponent, type RefAttributes } from 'react';

// material-ui

// types
import { KeyedObject } from 'types';

// constant
const headerSX = {
  '& .MuiCardHeader-action': { mr: 0 },
};

// ==============================|| CUSTOM MAIN CARD ||============================== //

export interface MainCardProps extends KeyedObject {
  border?: boolean;
  boxShadow?: boolean;
  children?: React.ReactNode | string;
  style?: React.CSSProperties;
  content?: boolean;
  className?: string;
  contentClass?: string;
  contentSX?: CardContentProps['sx'];
  darkTitle?: boolean;
  sx?: CardProps['sx'];
  secondary?: CardHeaderProps['action'];
  shadow?: string;
  elevation?: number;
  title?: React.ReactNode | string;
}

const MainCard = React.forwardRef(
  (
    {
      border = false,
      boxShadow,
      children,
      content = true,
      contentClass = '',
      contentSX = {},
      darkTitle,
      secondary,
      shadow,
      sx = {},
      title,
      ...others
    }: MainCardProps,
    ref: Ref<HTMLDivElement>
  ) => {
    const theme = useTheme();

    return (
      <Card
        ref={ref}
        {...others}
        sx={{
          border: border ? '1px solid' : 'none',
          borderColor:
            theme.palette.mode === 'dark'
              ? theme.palette.background.default
              : `${(theme.palette as any).grey?.[300] ?? '#e0e0e0'}98`,
          ':hover': {
            boxShadow: boxShadow
              ? shadow ||
                (theme.palette.mode === 'dark'
                  ? '0 2px 14px 0 rgb(33 150 243 / 10%)'
                  : '0 2px 14px 0 rgb(32 40 45 / 8%)')
              : 'inherit',
          },
          ...sx,
        }}
      >
        {/* card header and action */}
        {!darkTitle && title && <CardHeader sx={headerSX} title={title} action={secondary} />}
        {darkTitle && title && (
          <CardHeader
            sx={headerSX}
            title={<Typography variant="h3">{title}</Typography>}
            action={secondary}
          />
        )}

        {/* content & header divider */}
        {title && <Divider />}

        {/* card content */}
        {content && (
          <CardContent sx={contentSX} className={contentClass}>
            {children}
          </CardContent>
        )}
        {!content && children}
      </Card>
    );
  }
);

// default export will be reassigned below after attaching statics with proper types

// Compound-like API for progressive migration
export const Header: React.FC<CardHeaderProps> = (props) => <CardHeader {...props} />;

export const Body: React.FC<React.PropsWithChildren<{ sx?: CardContentProps['sx']; className?: string }>> = ({
  children,
  sx,
  className,
}) => <CardContent sx={sx ?? {}} className={className ?? ''}>{children}</CardContent>;

export const Actions: React.FC<React.PropsWithChildren<{ justifyContent?: 'flex-start' | 'center' | 'flex-end' }>> = ({
  children,
  justifyContent = 'flex-end',
}) => <MuiCardActions sx={{ justifyContent }}>{children}</MuiCardActions>;

// Attach statics to default export for usage: MainCard.Header, MainCard.Body, MainCard.Actions
type MainCardCompound = ForwardRefExoticComponent<Omit<MainCardProps, 'ref'> & RefAttributes<HTMLDivElement>> & {
  Header: React.FC<CardHeaderProps>;
  Body: React.FC<React.PropsWithChildren<{ sx?: CardContentProps['sx']; className?: string }>>;
  Actions: React.FC<React.PropsWithChildren<{ justifyContent?: 'flex-start' | 'center' | 'flex-end' }>>;
};

const MainCardWithStatics = MainCard as unknown as MainCardCompound;
MainCardWithStatics.Header = Header;
MainCardWithStatics.Body = Body;
MainCardWithStatics.Actions = Actions;

export default MainCardWithStatics;
