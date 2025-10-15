'use client';

import { Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import dynamic from 'next/dynamic';

// material-ui
import { Fragment } from 'react';

// third-party
import { TreeProps, TreeNodeProps } from 'react-organizational-chart';
const Tree = dynamic<TreeProps>(() => import('react-organizational-chart').then(mod => mod.Tree), {
  ssr: false,
});

const TreeNode = dynamic<TreeNodeProps>(
  () => import('react-organizational-chart').then(mod => mod.TreeNode),
  {
    ssr: false,
  }
);

// project imports
import Card from 'components/forms/chart/OrgChart/Card';
import DataCard from 'components/forms/chart/OrgChart/DataCard';

// types
import { TreeMiddleWare, TreeCardmiddleWare } from 'types/org-chart';
import MainCard from 'ui-component/cards/MainCard';

// ==============================|| ORGANIZATION CHARTS ||============================== //

const data = [
  {
    name: 'Anne Teak',
    role: 'CEO',
    avatar: 'https://i.pravatar.cc/100?img=3',
    linkedin: 'https://www.linkedin.com/',
    meet: 'https://meet.google.com/',
    skype: 'https://www.skype.com/en/',
    children: [
      {
        name: 'Colin Sik',
        role: 'CFO',
        avatar: 'https://i.pravatar.cc/100?img=1',
        linkedin: 'https://www.linkedin.com/',
        meet: 'https://meet.google.com/',
        skype: 'https://www.skype.com/en/',
        children: [
          {
            name: 'Karen Onnabit',
            role: 'Controller',
            avatar: 'https://i.pravatar.cc/100?img=2',
            linkedin: 'https://www.linkedin.com/',
            meet: 'https://meet.google.com/',
            skype: 'https://www.skype.com/en/',
          },
        ],
      },
      {
        name: 'Jen Tile',
        role: 'VP of Marketing',
        avatar: 'https://i.pravatar.cc/100?img=4',
        linkedin: 'https://www.linkedin.com/',
        meet: 'https://meet.google.com/',
        skype: 'https://www.skype.com/en/',

        children: [
          {
            name: 'Anne Thurium',
            role: 'Manager',
            avatar: 'https://i.pravatar.cc/100?img=5',
            linkedin: 'https://www.linkedin.com/',
            meet: 'https://meet.google.com/',
            skype: 'https://www.skype.com/en/',

            children: [
              {
                name: 'Liz Erd',
                role: 'Junior Manager',
                avatar: 'https://i.pravatar.cc/100?img=6',
                linkedin: 'https://www.linkedin.com/',
                meet: 'https://meet.google.com/',
                skype: 'https://www.skype.com/en/',
              },
              {
                name: 'Percy Vere',
                role: 'Programmer',
                avatar: 'https://i.pravatar.cc/100?img=7',
                linkedin: 'https://www.linkedin.com/',
                meet: 'https://meet.google.com/',
                skype: 'https://www.skype.com/en/',
              },
            ],
          },
        ],
      },
      {
        name: 'Harriet Upp',
        role: 'VP of IT',
        avatar: 'https://i.pravatar.cc/100?img=8',
        linkedin: 'https://www.linkedin.com/',
        meet: 'https://meet.google.com/',
        skype: 'https://www.skype.com/en/',

        children: [
          {
            name: 'Mark Ateer',
            role: 'System Admin',
            avatar: 'https://i.pravatar.cc/100?img=6',
            linkedin: 'https://www.linkedin.com/',
            meet: 'https://meet.google.com/',
            skype: 'https://www.skype.com/en/',
          },
          {
            name: 'Dave Allippa',
            role: 'System Admin',
            avatar: 'https://i.pravatar.cc/100?img=7',
            linkedin: 'https://www.linkedin.com/',
            meet: 'https://meet.google.com/',
            skype: 'https://www.skype.com/en/',
          },
        ],
      },
    ],
  },
];

function SimpleTree({ name }: TreeMiddleWare) {
  const theme = useTheme();

  return (
    <Typography
      sx={{
        p: 2,
        border: `1px solid ${theme.palette.mode === 'dark' ? theme.palette.secondary.main : theme.palette.primary.main}`,
        width: 'max-content',
        m: 'auto',
        color: theme.palette.mode === 'dark' ? `text.secondary` : `secondary.dark`,
        bgcolor: theme.palette.mode === 'dark' ? `background.default` : `secondary.light`,
        borderRadius: 1,
      }}
    >
      {name}
    </Typography>
  );
}

function TreeCard({ items }: TreeCardmiddleWare) {
  return (
    <>
      {items.map((item: any, id: any) => (
        <Fragment key={id}>
          {item.children ? (
            <TreeNode label={<SimpleTree name={item.name} />}>
              <TreeCard items={item.children} />
            </TreeNode>
          ) : (
            <TreeNode label={<SimpleTree name={item.name} />} />
          )}
        </Fragment>
      ))}
    </>
  );
}

const OrgChart = () => {
  const theme = useTheme();

  return (
    <Grid container rowSpacing={2} justifyContent="center">
      <Grid size={{ xs: 12 }}>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12 }}>
            <MainCard title="Simple Chart" sx={{ overflow: 'auto', direction: theme.direction }}>
              <Tree
                lineWidth="1px"
                lineColor={theme.palette.secondary.main}
                lineBorderRadius="10px"
                label={<SimpleTree name={data[0]?.name ?? ''} />}
              >
                <TreeCard items={data[0]?.children ?? []} />
              </Tree>
            </MainCard>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <MainCard title="Styled Chart" sx={{ overflow: 'auto', direction: theme.direction }}>
              <Tree
                lineWidth="1px"
                lineColor={theme.palette.secondary.main}
                lineBorderRadius="10px"
                label={
                  <DataCard
                    name={data[0]?.name ?? ''}
                    role={data[0]?.role ?? ''}
                    avatar={data[0]?.avatar ?? ''}
                    linkedin={data[0]?.linkedin ?? ''}
                    meet={data[0]?.meet ?? ''}
                    skype={data[0]?.skype ?? ''}
                    root
                  />
                }
              >
                <Card items={data[0]?.children ?? []} />
              </Tree>
            </MainCard>
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};

export default OrgChart;
