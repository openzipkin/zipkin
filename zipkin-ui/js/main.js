import {compose, registry, advice, debug} from 'flightjs';
import crossroads from 'crossroads';
import initializeDefault from './page/default';
import initializeTrace from './page/trace';
import initializeDependency from './page/dependency';
import CommonUI from './page/common';

debug.enable(true);
compose.mixin(registry, [advice.withAdvice]);

CommonUI.attachTo(window.document.body);

crossroads.addRoute('', initializeDefault);
crossroads.addRoute('traces/{id}', initializeTrace);
crossroads.addRoute('dependency', initializeDependency);
crossroads.parse(window.location.pathname);
