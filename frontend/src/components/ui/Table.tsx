import React, { type HTMLAttributes, type TdHTMLAttributes, type ThHTMLAttributes } from 'react';

export const Table = ({ className = '', ...props }: HTMLAttributes<HTMLTableElement>) => (
  <div className="w-full overflow-x-auto rounded-lg border border-gray-200 dark:border-slate-800">
    <table className={`w-full text-left border-collapse ${className}`} {...props} />
  </div>
);

export const TableHeader = ({ className = '', children, ...props }: HTMLAttributes<HTMLTableSectionElement>) => (
  <thead className={`bg-gray-50 dark:bg-slate-800/50 border-b border-gray-200 dark:border-slate-800 text-gray-600 dark:text-slate-400 text-sm ${className}`} {...props}>
    <tr>{children}</tr>
  </thead>
);

export const TableBody = ({ className = '', children, items, renderEmptyState, ...props }: Omit<HTMLAttributes<HTMLTableSectionElement>, 'children'> & { items?: any[], renderEmptyState?: () => React.ReactNode, children?: React.ReactNode | ((item: any, index: number) => React.ReactNode) }) => {
  return (
    <tbody className={`divide-y divide-gray-100 dark:divide-slate-800/50 ${className}`} {...props}>
      {items && items.length === 0 && renderEmptyState ? (
        <tr>
          <td colSpan={100} className="p-0">
            {renderEmptyState()}
          </td>
        </tr>
      ) : items && typeof children === 'function' ? (
        items.map((item, index) => children(item, index))
      ) : (
        children as React.ReactNode
      )}
    </tbody>
  );
};

export const TableRow = ({ className = '', ...props }: HTMLAttributes<HTMLTableRowElement>) => (
  <tr className={`hover:bg-gray-50 dark:hover:bg-slate-800/50 transition-colors ${className}`} {...props} />
);

export const TableColumn = ({ className = '', width, ...props }: ThHTMLAttributes<HTMLTableCellElement> & { width?: number | string }) => (
  <th 
    className={`px-4 py-3 font-medium ${className}`} 
    style={width ? { width: typeof width === 'number' ? `${width}px` : width } : undefined}
    {...props} 
  />
);

export const TableCell = ({ className = '', ...props }: TdHTMLAttributes<HTMLTableCellElement>) => (
  <td className={`px-4 py-3 ${className}`} {...props} />
);
