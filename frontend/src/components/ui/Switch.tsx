import { type InputHTMLAttributes } from 'react';

export const Switch = ({ 
  isSelected, 
  onChange, 
  className = '',
  size = 'md',
  ...props 
}: { 
  isSelected?: boolean; 
  onChange?: (checked: boolean) => void;
  size?: 'sm' | 'md' | 'lg';
} & Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'size'>) => {
  
  const sizeMap = {
    sm: { wrapper: 'w-8 h-4', thumb: 'h-3 w-3', trans: 'peer-checked:translate-x-4' },
    md: { wrapper: 'w-11 h-6', thumb: 'h-5 w-5', trans: 'peer-checked:translate-x-5' },
    lg: { wrapper: 'w-14 h-7', thumb: 'h-6 w-6', trans: 'peer-checked:translate-x-7' }
  };
  
  const s = sizeMap[size];

  return (
    <label className={`relative inline-flex items-center cursor-pointer ${className}`}>
      <input 
        type="checkbox" 
        className="sr-only peer"
        checked={isSelected}
        onChange={(e) => onChange?.(e.target.checked)}
        {...props}
      />
      <div className={`${s.wrapper} bg-gray-200 dark:bg-slate-700 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-blue-500 rounded-full peer peer-checked:bg-blue-600 transition-colors`}>
        <div className={`${s.thumb} absolute top-[2px] left-[2px] bg-white border border-gray-300 dark:border-transparent rounded-full transition-all ${s.trans}`}></div>
      </div>
    </label>
  );
};

