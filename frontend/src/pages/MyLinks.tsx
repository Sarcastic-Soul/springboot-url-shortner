import { useEffect, useState } from 'react';
import { Button } from '../components/ui/Button';
import { Switch } from '../components/ui/Switch';
import { Card, CardContent } from '../components/ui/Card';
import { Skeleton } from '../components/ui/Skeleton';
import { Table, TableHeader, TableBody, TableColumn, TableRow, TableCell } from '../components/ui/Table';
import { Checkbox } from '../components/ui/Checkbox';
import { Modal, ModalHeader, ModalBody, ModalFooter } from '../components/ui/Modal';
import { IconCopy, IconChartBar, IconTrash } from '@tabler/icons-react';
import toast from 'react-hot-toast';
import { urlApi } from '../api/urls';
import { analyticsApi } from '../api/analytics';
import { apiClient, type UrlSummaryResponse, type PageResponse, type AnalyticsResponse } from '../api/client';

export default function MyLinks() {
  const [page, setPage] = useState(1);
  const [urls, setUrls] = useState<PageResponse<UrlSummaryResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  
  const [isOpen, setIsOpen] = useState(false);
  const onOpen = () => setIsOpen(true);
  const onOpenChange = (open: boolean) => setIsOpen(open);

  const [selectedAnalytics, setSelectedAnalytics] = useState<AnalyticsResponse | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);

  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());

  const fetchUrls = async () => {
    try {
      setLoading(true);
      const data = await urlApi.getMyUrls(page - 1, 10);
      setUrls(data);
    } catch (err) {
      toast.error('Failed to fetch URLs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUrls();
  }, [page]);

  const handleCopy = (shortUrl: string) => {
    navigator.clipboard.writeText(shortUrl);
    toast.success('URL copied to clipboard');
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this link?')) return;
    try {
      await urlApi.deleteUrl(id);
      toast.success('Link deleted successfully');
      fetchUrls();
    } catch (err) {
      toast.error('Failed to delete link');
    }
  };

  const handleBulkDelete = async () => {
    const selected = Array.from(selectedKeys);
    if (selected.length === 0) return;
    if (!window.confirm(`Are you sure you want to delete ${selected.length} selected links?`)) return;
    try {
      await apiClient.delete('/urls/bulk', { data: selected });
      toast.success(`${selected.length} links deleted`);
      setSelectedKeys(new Set());
      fetchUrls();
    } catch (err) {
      toast.error('Failed to delete links');
    }
  };

  const toggleStatus = async (url: UrlSummaryResponse) => {
    try {
      await urlApi.updateUrl(url.id, { active: !url.active });
      toast.success(`Link ${!url.active ? 'enabled' : 'disabled'} successfully`);
      fetchUrls();
    } catch (err) {
      toast.error('Failed to update status');
    }
  };

  const viewAnalytics = async (id: string) => {
    onOpen();
    setAnalyticsLoading(true);
    try {
      const data = await analyticsApi.getAnalytics(id);
      setSelectedAnalytics(data);
    } catch (err) {
      toast.error('Failed to fetch analytics');
    } finally {
      setAnalyticsLoading(false);
    }
  };

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedKeys(new Set(urls?.content.map(u => u.id) || []));
    } else {
      setSelectedKeys(new Set());
    }
  };

  const handleSelectRow = (id: string, checked: boolean) => {
    const newSet = new Set(selectedKeys);
    if (checked) {
      newSet.add(id);
    } else {
      newSet.delete(id);
    }
    setSelectedKeys(newSet);
  };

  return (
    <div className="container mx-auto px-4 max-w-6xl mt-12 mb-20">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-3xl font-bold">My Links</h2>
        {selectedKeys.size > 0 && (
          <Button 
            variant="danger-soft" 
            onPress={handleBulkDelete}
          >
            <IconTrash size={16} /> Delete Selected ({selectedKeys.size})
          </Button>
        )}
      </div>
      
      <div className="w-full shadow-sm rounded-lg border border-divider">
        <Table aria-label="My shortened links">
          <TableHeader>
            <TableColumn style={{ width: '50px' }}>
              <Checkbox 
                isSelected={urls?.content.length !== 0 && selectedKeys.size === urls?.content.length}
                onChange={handleSelectAll}
              />
            </TableColumn>
            <TableColumn>Title / Original URL</TableColumn>
            <TableColumn>Short Code</TableColumn>
            <TableColumn className="text-center">Clicks</TableColumn>
            <TableColumn className="text-center">Status</TableColumn>
            <TableColumn className="text-center">Actions</TableColumn>
          </TableHeader>
          <TableBody 
            renderEmptyState={() => (
              <div className="flex flex-col items-center justify-center p-8">
                You have no shortened links.
              </div>
            )}
            items={loading ? Array(5).fill({ isSkeleton: true }) : urls?.content || []}
          >
            {(url: any, index: number) => url.isSkeleton ? (
              <TableRow key={`skeleton-${index}`}>
                <TableCell><Skeleton className="h-5 w-5" /></TableCell>
                <TableCell>
                  <div className="flex flex-col gap-2">
                    <Skeleton className="h-5 w-40" />
                    <Skeleton className="h-3 w-64" />
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-8 w-8 rounded-md" />
                  </div>
                </TableCell>
                <TableCell><Skeleton className="h-4 w-12" /></TableCell>
                <TableCell><Skeleton className="h-6 w-20 rounded-full" /></TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <Skeleton className="h-8 w-8 rounded-md" />
                    <Skeleton className="h-8 w-8 rounded-md" />
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              <TableRow key={url.id}>
                <TableCell>
                  <Checkbox 
                    isSelected={selectedKeys.has(url.id)}
                    onChange={(checked) => handleSelectRow(url.id, checked)}
                  />
                </TableCell>
                <TableCell>
                  <div className="flex flex-col">
                    <span className="font-medium text-foreground">{url.title || 'Untitled'}</span>
                    <span className="text-xs text-default-500 truncate max-w-[250px]">{url.originalUrl}</span>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-mono">{url.shortCode}</span>
                    <Button 
                      isIconOnly 
                      size="sm" 
                      variant="ghost" 
                      onPress={() => handleCopy(url.shortUrl)}
                    >
                      <IconCopy size={16} />
                    </Button>
                  </div>
                </TableCell>
                <TableCell>{url.clickCount}</TableCell>
                <TableCell>
                  <div className="flex items-center justify-center gap-2">
                    <Switch 
                      size="sm" 
                      isSelected={url.active} 
                      onChange={() => toggleStatus(url)}
                    />
                    <span className={`text-xs px-2 py-1 rounded-full ${url.active ? 'bg-success/20 text-success' : 'bg-danger/20 text-danger'}`}>
                      {url.active ? 'Active' : 'Disabled'}
                    </span>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex items-center justify-center gap-2">
                    <Button 
                      isIconOnly 
                      size="sm" 
                      variant="ghost" 
                      onPress={() => viewAnalytics(url.id)}
                    >
                      <IconChartBar size={18} />
                    </Button>
                    <Button 
                      isIconOnly 
                      size="sm" 
                      variant="danger-soft" 
                      onPress={() => handleDelete(url.id)}
                    >
                      <IconTrash size={18} />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        {urls && urls.page.totalPages > 1 && (
          <div className="flex w-full justify-between items-center mt-4 pb-4 px-4">
            <Button
              variant="ghost"
              isDisabled={page === 1}
              onPress={() => setPage((p) => Math.max(1, p - 1))}
            >
              Previous
            </Button>
            <span className="text-sm text-default-500">Page {page} of {urls.page.totalPages}</span>
            <Button
              variant="ghost"
              isDisabled={page === urls.page.totalPages}
              onPress={() => setPage((p) => Math.min(urls.page.totalPages, p + 1))}
            >
              Next
            </Button>
          </div>
        )}
      </div>

      <Modal isOpen={isOpen} onOpenChange={onOpenChange}>
        <Modal.Content>
          <ModalHeader onClose={() => onOpenChange(false)}>Link Analytics</ModalHeader>
          <ModalBody>
            {analyticsLoading ? (
              <div className="flex flex-col gap-8">
                <div className="grid grid-cols-2 gap-4">
                  <Card className="shadow-none border border-gray-200 dark:border-slate-800">
                    <CardContent className="p-4 flex flex-col items-center justify-center gap-2">
                      <Skeleton className="w-24 h-4" />
                      <Skeleton className="w-16 h-8" />
                    </CardContent>
                  </Card>
                  <Card className="shadow-none border border-gray-200 dark:border-slate-800">
                    <CardContent className="p-4 flex flex-col items-center justify-center gap-2">
                      <Skeleton className="w-28 h-4" />
                      <Skeleton className="w-16 h-8" />
                    </CardContent>
                  </Card>
                </div>
                <div>
                  <Skeleton className="w-32 h-6 mb-3" />
                  <Skeleton className="w-full h-48 rounded-lg" />
                </div>
              </div>
            ) : selectedAnalytics ? (
              <div className="flex flex-col gap-8">
                <div className="grid grid-cols-2 gap-4">
                  <Card className="shadow-none border border-gray-200">
                    <CardContent className="p-4 flex flex-col items-center justify-center">
                      <span className="text-gray-500 dark:text-slate-400 text-sm">Total Clicks</span>
                      <span className="text-3xl font-bold text-blue-600">{selectedAnalytics.totalClicks}</span>
                    </CardContent>
                  </Card>
                  <Card className="shadow-none border border-gray-200">
                    <CardContent className="p-4 flex flex-col items-center justify-center">
                      <span className="text-gray-500 dark:text-slate-400 text-sm">Unique Visitors</span>
                      <span className="text-3xl font-bold text-indigo-600">{(selectedAnalytics as any).uniqueVisitors || 'N/A'}</span>
                    </CardContent>
                  </Card>
                </div>
                
                <div>
                  <h4 className="text-lg font-medium mb-3">Recent Clicks</h4>
                  <div className="bg-gray-50 dark:bg-slate-800/50 p-4 rounded-lg border border-gray-200 dark:border-slate-800">
                    {selectedAnalytics.recentClicks.length === 0 ? (
                      <p className="text-gray-500 dark:text-slate-400 italic">No clicks recorded yet.</p>
                    ) : (
                      <Table aria-label="Recent clicks">
                        <TableHeader>
                          <TableColumn>Time</TableColumn>
                          <TableColumn>IP</TableColumn>
                          <TableColumn>Device/OS</TableColumn>
                          <TableColumn>Browser</TableColumn>
                        </TableHeader>
                        <TableBody items={selectedAnalytics.recentClicks}>
                          {(click: any) => (
                            <TableRow key={click.clickedAt + click.ipAddress}>
                              <TableCell className="text-gray-600 dark:text-slate-300">{new Date(click.clickedAt).toLocaleString()}</TableCell>
                              <TableCell className="font-mono">{click.ipAddress}</TableCell>
                              <TableCell>{click.device} / {click.os}</TableCell>
                              <TableCell>{click.browser}</TableCell>
                            </TableRow>
                          )}
                        </TableBody>
                      </Table>
                    )}
                  </div>
                </div>
              </div>
            ) : (
              <p className="text-red-500 text-center">Failed to load analytics.</p>
            )}
          </ModalBody>
          <ModalFooter>
            <Button variant="ghost" onPress={() => onOpenChange(false)}>
              Close
            </Button>
          </ModalFooter>
        </Modal.Content>
      </Modal>
    </div>
  );
}
