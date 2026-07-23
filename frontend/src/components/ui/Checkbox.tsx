import React, { type InputHTMLAttributes } from 'react';

export const Checkbox = ({ 
  isSelected, 
  onChange, 
  className = '', 
  children,
  ...props 
}: { 
  isSelected?: boolean; 
  onChange?: (checked: boolean) => void;
  children?: React.ReactNode;
} & Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange'>) => {
  return (
    <label className={`inline-flex items-center gap-2 cursor-pointer ${className}`}>
      <div className="relative flex items-center">
        <input 
          type="checkbox"
          className="peer h-5 w-5 cursor-pointer appearance-none rounded border border-gray-300 bg-white transition-all checked:border-blue-600 checked:bg-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-1"
          checked={isSelected}
          onChange={(e) => onChange?.(e.target.checked)}
          {...props}
        />
        <svg
          className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-3.5 h-3.5 text-white opacity-0 transition-opacity peer-checked:opacity-100 pointer-events-none"
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <polyline points="20 6 9 17 4 12"></polyline>
        </svg>
      </div>
      {children && <span className="text-sm text-gray-700 select-none">{children}</span>}
    </label>
  );
};
