import React, { type ReactNode } from 'react';
import { IconChevronDown } from '@tabler/icons-react';

export const Select = {
  Root: ({ 
    children, 
    className = '', 
    selectedKey, 
    onSelectionChange 
  }: { 
    children: ReactNode, 
    className?: string, 
    selectedKey?: string, 
    onSelectionChange?: (val: string) => void 
  }) => {
    let options: { id: string, label: string }[] = [];
    
    React.Children.forEach(children, child => {
      if (React.isValidElement(child) && (child.type as any).displayName === 'SelectPopover') {
        React.Children.forEach((child.props as any).children, listbox => {
          if (React.isValidElement(listbox)) {
            React.Children.forEach((listbox.props as any).children, item => {
              if (React.isValidElement(item)) {
                options.push({
                  id: (item.props as any).id || (item.props as any).value,
                  label: (item.props as any).textValue || (item.props as any).children as string
                });
              }
            });
          }
        });
      }
    });

    return (
      <div className={`relative ${className}`}>
        <select
          value={selectedKey}
          onChange={(e) => onSelectionChange?.(e.target.value)}
          className="appearance-none w-full h-12 px-3 pr-10 bg-gray-100 dark:bg-slate-800 hover:bg-gray-200 dark:hover:bg-slate-700 transition-colors border-none rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 cursor-pointer text-slate-900 dark:text-slate-100"
        >
          {options.map(opt => (
            <option key={opt.id} value={opt.id}>{opt.label}</option>
          ))}
        </select>
        <div className="absolute inset-y-0 right-3 flex items-center pointer-events-none text-gray-500 dark:text-slate-400">
          <IconChevronDown size={18} />
        </div>
      </div>
    );
  },
  Trigger: ({ children }: any) => <>{children}</>,
  Value: () => null,
  Indicator: () => null,
  Popover: ({ children }: any) => {
    const PopoverComponent = () => <>{children}</>;
    return <PopoverComponent />;
  }
};
(Select.Popover as any).displayName = 'SelectPopover';

export const ListBox = ({ children }: any) => <>{children}</>;
ListBox.Item = ({ children }: any) => <>{children}</>;
