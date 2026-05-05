const fs = require('fs');
const path = require('path');

const dir = 'src/pages/author';

// Fix CategoryManage
let cat = fs.readFileSync(path.join(dir, 'CategoryManage.tsx'), 'utf8');
cat = cat.replace(
  'const [categories, setCategories] = useState<Category[]>([]);\n  const [loading, setLoading] = useState(false);\n  const [modalVisible, setModalVisible] = useState(false);',
  `const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [modalVisible, setModalVisible] = useState(false);`
);
cat = cat.replace(
  "getAuthorCategories(100).then(res => setCategories(res.data))",
  "getAuthorCategories({ page, pageSize }).then(res => { setCategories(res.data.list); setTotal(res.data.total); })"
);
cat = cat.replace(
  'useEffect(() => { loadData(); }, []);',
  'useEffect(() => { loadData(); }, [page, pageSize]);'
);
cat = cat.replace(
  '<Table columns={columns} dataSource={categories.map(c => ({ ...c, key: c.id }))} loading={loading} pagination={false} />',
  `<Table
          columns={columns}
          dataSource={categories.map(c => ({ ...c, key: c.id }))}
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showTotal: (t) => \`共 \${t} 条\`,
            onChange: (p, ps) => { setPage(p); setPageSize(ps); },
          }}
        />`
);
fs.writeFileSync(path.join(dir, 'CategoryManage.tsx'), cat);
console.log('CategoryManage fixed');

// Fix SeriesManage
let ser = fs.readFileSync(path.join(dir, 'SeriesManage.tsx'), 'utf8');

// Add pagination state vars
ser = ser.replace(
  'const [categories, setCategories] = useState<Category[]>([]);\n  const [loading, setLoading] = useState(false);\n  const [modalVisible, setModalVisible] = useState(false);',
  `const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [modalVisible, setModalVisible] = useState(false);`
);

// Fix loadData to use paginated params
ser = ser.replace(
  'getAuthorSeries(100),\n      getAuthorCategories(100),',
  'getAuthorSeries({ page, pageSize }),\n      getAuthorCategories({ page: 1, pageSize: 100 }),'
);
ser = ser.replace(
  'setSeriesList(res.data);\n      setCategories(catRes.data);',
  'setSeriesList(seriesRes.data.list);\n      setTotal(seriesRes.data.total);\n      setCategories(catRes.data.list);'
);
ser = ser.replace(
  'setSeriesList(seriesRes.data);\n      setTotal(seriesRes.data.total);',
  'setSeriesList(seriesRes.data.list);\n      setTotal(seriesRes.data.total);'
);

// Fix effect deps
ser = ser.replace(
  'useEffect(() => { loadData(); }, [page, pageSize]);',
  'useEffect(() => { loadData(); }, [page, pageSize]);'
);

// Fix table pagination
ser = ser.replace(
  'pagination={false}',
  `pagination={{
            current: page,
            pageSize,
            total,
            showTotal: (t) => \`共 \${t} 条\`,
            onChange: (p, ps) => { setPage(p); setPageSize(ps); },
          }}`
);

fs.writeFileSync(path.join(dir, 'SeriesManage.tsx'), ser);
console.log('SeriesManage fixed');
