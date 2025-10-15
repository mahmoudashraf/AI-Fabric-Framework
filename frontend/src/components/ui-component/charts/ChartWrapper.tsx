'use client';

import { useEffect, useState, useRef } from 'react';

type ChartType = "bar" | "area" | "line" | "pie" | "donut" | "radialBar" | "scatter" | "bubble" | "heatmap" | "candlestick" | "boxPlot" | "radar" | "polarArea" | "rangeBar" | "rangeArea" | "treemap";

interface ChartWrapperProps {
  options: any;
  series: any;
  type: ChartType;
  height?: number | string;
  width?: number | string;
  className?: string;
  style?: React.CSSProperties;
}

// Chart wrapper component with complete isolation from Next.js dynamic APIs
const ChartWrapper = ({ options, series, type, height, width, className, style }: ChartWrapperProps) => {
  const [isClient, setIsClient] = useState(false);
  const [isMounted, setIsMounted] = useState(false);
  const [ChartComponent, setChartComponent] = useState<any>(null);
  const chartRef = useRef<HTMLDivElement>(null);
  
  useEffect(() => {
    setIsClient(true);
    
    // Import the chart component only on client side with a delay
    const loadChart = async () => {
      try {
        // Wait for the component to be fully mounted
        await new Promise(resolve => setTimeout(resolve, 200));
        
        // Dynamic import only after component is mounted
        const { default: ReactApexChart } = await import('react-apexcharts');
        setChartComponent(() => ReactApexChart);
        setIsMounted(true);
      } catch (error) {
        console.error('Failed to load chart component:', error);
        setIsMounted(true);
      }
    };
    
    loadChart();
  }, []);
  
  if (!isClient || !isMounted || !ChartComponent) {
    return (
      <div 
        ref={chartRef}
        style={{ 
          height: height || 300, 
          width: width || '100%',
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'center',
          backgroundColor: '#f5f5f5',
          borderRadius: '4px',
          ...style 
        }}
        className={className}
      >
        <div style={{ color: '#666', fontSize: '14px' }}>Loading chart...</div>
      </div>
    );
  }
  
  return (
    <div ref={chartRef} style={{ height: height || 300, width: width || '100%', ...style }} className={className}>
      <ChartComponent 
        options={options} 
        series={series} 
        type={type} 
        height={height}
        width={width}
      />
    </div>
  );
};

export default ChartWrapper;