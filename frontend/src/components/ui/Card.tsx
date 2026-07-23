import { type HTMLAttributes } from 'react';

export const Card = ({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div className={`bg-white dark:bg-slate-900 rounded-xl shadow-sm border border-gray-200 dark:border-slate-800 overflow-hidden ${className}`} {...props} />
);

export const CardHeader = ({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div className={`px-6 py-4 border-b border-gray-100 dark:border-slate-800 flex flex-col gap-1 ${className}`} {...props} />
);

export const CardContent = ({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div className={`p-6 ${className}`} {...props} />
);
