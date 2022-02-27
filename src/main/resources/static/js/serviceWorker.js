//https://zhuanlan.zhihu.com/p/144512343
//https://www.cnblogs.com/yangyangxxb/p/9964959.html
//https://blog.csdn.net/qq_42062727/article/details/106012363
const cacheName = 'v1.0.0'; //定义 cache 名字
const cacheFiles = [ //定义需要缓存的文件
	'/',
	'./favicon.ico',
	'./js/jquery-3.4.1.min.js',
	'./js/coo.js',
	'./js/coo.mobile.js',
	'./js/coo.pc.js',
	'./js/common.js',
	'./css/base.css',
	'./css/base.mobile.css',
	'./css/mobile.css',
];

/*
if ('serviceWorker' in navigator) {
	window.addEventListener('load', function () {
		navigator.serviceWorker.register('/js/serviceWorker.js', {scope:'/'}).then(registration => {
			// 注册好后，会返回一个 promise，数据是 woker 上下文运行环境对象
			console.log('ServiceWorker registration successful with scope: ', registration.scope)
		}).catch(err => {
			console.log('ServiceWorker registration failed: ', err)
		})
	})
}
*/

self.addEventListener('install',async e => {
	const cache = await caches.open(cacheName);
	await cache.addAll(cacheFiles); //把资源放入 cache 中
	await self.skipWaiting(); //这里使用了 async/await，就不用在使用 event.waitUntil 了
	/*
	//找到key对应的缓存并且获得可以操作的cache对象
	let cacheOpenPromise = caches.open(cacheName).then(function (cache) {
		//将需要缓存的文件加进来
		return cache.addAll(cacheFiles)
	})
	//将promise对象传给event
	e.waitUntil(cacheOpenPromise)
	*/
});

self.addEventListener('activate', async () => {
	const keys = await caches.keys();
	for (let k of keys) {
		if (k !== cacheName) {
			await caches.delete(k);
		}
	}
	await self.clients.claim();
});

self.addEventListener('fetch', async e => {
	// 注意，event.request 页面发出的请求
	// 而 caches.match 根据请求匹配本地缓存中有没有相应的资源
	async function getResponse(){
		try {
			if (navigator.onLine) { //onLine 是 true，表示有网
				let response = await fetch(e.request);
				let cache = await caches.open(cacheName);
				await cache.put(e.request, response.clone());
				return response;
			} else {
				return await caches.match(e.request);
			}
		} catch (error) {
			// 也有可能在请求途中我们网断了，这时候需要判断一下缓存中有没有数据
			let res = await caches.match(e.request);
			if (!res) return await fetch(e.request);
			return res;
		}
	}
	e.respondWith(
		getResponse()
	);
});
