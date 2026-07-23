import { type HTMLAttributes } from 'react';

export const Skeleton = ({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div 
    className={`animate-pulse bg-gray-200 dark:bg-slate-700 rounded-md ${className}`} 
    {...props} 
  />
);
